package com.aiworkflow.designer.agent.impl;

import com.aiworkflow.designer.agent.core.BaseAgent;
import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.conversation.memory.ConversationContext;
import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.persistence.entity.WorkflowSpec;
import com.aiworkflow.designer.requirements.service.RequirementSpecService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Conversational intake agent. Talks to the client, builds a structured
 * {@link WorkflowSpec}, presents a summary for explicit confirmation, and only
 * then signals the orchestrator to hand off to the Designer agent.
 */
@Slf4j
@Component
public class ConsultantAgent extends BaseAgent {

    private static final Set<String> CONFIRMATION_WORDS = Set.of(
            "yes", "yeah", "yep", "confirm", "confirmed", "correct", "right",
            "looks good", "sounds good", "ok", "okay", "sure", "proceed",
            "go ahead", "build", "generate", "create it", "build it",
            "that's right", "that is correct", "approved", "approve");

    private final RequirementSpecService specService;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;

    public ConsultantAgent(ChatClient chatClient, ChatModel chatModel,
                           RequirementSpecService specService, ObjectMapper objectMapper,
                           KnowledgeService knowledgeService, ChatMemory chatMemory) {
        super(chatClient, chatModel, knowledgeService);
        this.specService = specService;
        this.objectMapper = objectMapper;
        this.chatMemory = chatMemory;
    }

    @Override
    public String getAgentType() {
        return "CONSULTANT";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a business process consultant for an AI-powered BPMN workflow designer.
            Your job is to help a business client describe the process they want to automate,
            BEFORE any workflow is built.

            Behavior rules:
            1. Be conversational, concise and professional. Greet back on greetings.
            2. Never assume the client wants a workflow from a greeting or vague message.
            3. When the client wants to design a workflow, collect at minimum:
               - the process name and goal
               - the main steps in order
               - the actors/roles involved
               - decision points and their conditions
            4. Ask one or two focused questions at a time; do not interrogate.
            5. When you have enough information, present a structured summary and ask
               for explicit confirmation before building.
            """;
    }

    @Override
    public AgentState process(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("ConsultantAgent processing request ({} chars)", userMessage.length());
        AgentState state = beginRequest();
        updateState("THINKING");

        try {
            state.addContext("user_message", userMessage);
            UUID convId = parseUuid(conversationId);
            WorkflowSpec spec = convId != null
                    ? specService.getOrCreate(convId, ConversationContext.getUserId())
                    : new WorkflowSpec();

            // Confirm whenever the user says yes AND the spec has the essentials —
            // not only in PENDING_CONFIRMATION, so a confirmation is never ignored.
            if (isConfirmation(userMessage) && (spec.isPendingConfirmation() || isComplete(spec))) {
                spec.setStatus("CONFIRMED");
                if (spec.getId() != null) {
                    spec = specService.save(spec);
                }
                state.addContext("ready_to_build", true);
                state.addContext("spec_summary", buildSpecText(spec));
                state.addContext("spec", spec);
                String reply = "Confirmed. I'm building the workflow now — one moment.";
                persistTurn(conversationId, userMessage, reply);
                state.setLastAgentResponse(reply);
                updateState("COMPLETED");
                return state;
            }

            updateState("EXECUTING");
            updateSpecFromConversation(userMessage, spec, conversationId);
            state.addContext("spec", spec);

            if (isComplete(spec)) {
                spec.setStatus("PENDING_CONFIRMATION");
                if (spec.getId() != null) {
                    spec = specService.save(spec);
                }
                String reply = buildSummaryMessage(spec)
                        + "\n\nShall I create this workflow? Reply 'yes' to confirm, or tell me what to change.";
                persistTurn(conversationId, userMessage, reply);
                state.setLastAgentResponse(reply);
            } else {
                if (spec.getId() != null) {
                    specService.save(spec);
                }
                state.setLastAgentResponse(generateClarifyingReply(userMessage, spec, conversationId));
            }

            updateState("COMPLETED");
            return state;

        } catch (Exception e) {
            log.error("Consultant agent failed", e);
            state.updateStatus("FAILED");
            state.addContext("error", e.getMessage());
            state.setLastAgentResponse("I hit an issue processing that. Could you rephrase?");
            return state;
        } finally {
            endRequest();
        }
    }

    /**
     * Asks the LLM to merge the user's message plus prior conversation history
     * into the current spec, then persists the result. The extraction call runs
     * without the memory advisor so the internal prompt is never stored in history;
     * the transcript is passed explicitly instead.
     */
    private void updateSpecFromConversation(String userMessage, WorkflowSpec spec, String conversationId) {
        String currentJson;
        try {
            currentJson = objectMapper.writeValueAsString(toSpecJson(spec));
        } catch (Exception e) {
            currentJson = "{}";
        }

        String extractionPrompt = """
                Given the conversation so far and the current structured spec below,
                return ONLY an updated JSON object with these fields:
                processName (string), description (string),
                steps (array of {name, type, assignee}),
                actors (array of role names),
                decisions (array of {name, condition, outcomes}),
                conditions (array of strings),
                missingInfo (array of strings - only blocking questions, i.e. things
                that make the workflow impossible to build without; return an empty
                array once processName, steps and actors are clear).
                Keep existing values unless the user changed them. No markdown, no commentary.

                Conversation history:
                %s

                Current spec: %s

                Latest user message: %s
                """.formatted(transcript(conversationId), currentJson, userMessage);

        String raw = generateResponse(extractionPrompt, getSystemPrompt(), null);
        JsonNode node = parseJson(raw);
        if (node == null) {
            log.warn("Spec extraction returned no JSON: {}", raw);
            return;
        }

        applySpecJson(spec, node);
        if (spec.getId() != null) {
            specService.save(spec);
        }
    }

    private String transcript(String conversationId) {
        if (conversationId == null) {
            return "(none)";
        }
        List<Message> messages = chatMemory.get(conversationId, 20);
        if (messages == null || messages.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append(m.getMessageType() == MessageType.USER ? "User: " : "Assistant: ")
                    .append(m.getContent()).append('\n');
        }
        return sb.toString();
    }

    private String generateClarifyingReply(String userMessage, WorkflowSpec spec, String conversationId) {
        String reply = generateResponseWithContext(
                userMessage,
                getSystemPrompt() + """

                Respond to the client naturally. If they are describing a process,
                acknowledge it and ask one or two focused questions about what is still
                missing (see missingInfo). Do NOT produce a workflow or BPMN.
                NEVER show raw JSON or the spec object to the client - speak in plain
                business language only.
                """,
                "Current spec: " + describeSpecBrief(spec),
                conversationId);
        return reply != null ? reply : "Could you tell me more about the process you want to automate?";
    }

    /**
     * The spec is complete enough to build when it has a name, ordered steps and
     * actors. missingInfo is advisory only - the LLM can always invent more
     * questions, so requiring it to be empty deadlocks confirmation.
     */
    private boolean isComplete(WorkflowSpec spec) {
        return spec.getProcessName() != null && !spec.getProcessName().isBlank()
                && hasItems(spec.getSteps())
                && hasItems(spec.getActors());
    }

    private boolean hasItems(Object field) {
        return field instanceof List<?> list && !list.isEmpty();
    }

    private static final Set<String> CHANGE_WORDS = Set.of(
            "but", "change", "also", "add", "remove", "instead", "actually",
            "modify", "except", "however", "wait", "different");

    /**
     * Conservative confirmation check: affirmative keyword match only.
     * Messages that add or change requirements are NOT treated as confirmations.
     */
    static boolean isConfirmation(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.trim().toLowerCase();
        if (lower.length() > 80) {
            return false;
        }
        for (String word : CHANGE_WORDS) {
            if (lower.contains(" " + word + " ") || lower.contains(" " + word + ",")
                    || lower.endsWith(" " + word)) {
                return false;
            }
        }
        for (String word : CONFIRMATION_WORDS) {
            if (lower.equals(word) || lower.startsWith(word + ",") || lower.startsWith(word + ".")
                    || lower.startsWith(word + " ") || lower.endsWith(" " + word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Persists a user/assistant turn directly. Used on paths that never call the
     * LLM with the real conversationId (confirmation, static summary), where the
     * memory advisor would otherwise not record the exchange.
     */
    private void persistTurn(String conversationId, String userMessage, String reply) {
        if (conversationId == null) {
            return;
        }
        chatMemory.add(conversationId, List.of(
                new UserMessage(userMessage),
                new AssistantMessage(reply)));
    }

    /**
     * Extracts the first complete JSON object from the model output: strips
     * markdown fences, then scans for matching braces so a trailing code block
     * or commentary cannot corrupt the payload.
     */
    private JsonNode parseJson(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("(?s)```(?:json)?", "").trim();
        int start = cleaned.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        int end = -1;
        boolean inString = false, escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) { end = i; break; }
        }
        if (end < 0) {
            return null;
        }
        try {
            return objectMapper.readTree(cleaned.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private void applySpecJson(WorkflowSpec spec, JsonNode node) {
        if (node.hasNonNull("processName")) spec.setProcessName(node.get("processName").asText());
        if (node.hasNonNull("description")) spec.setDescription(node.get("description").asText());
        if (node.has("steps")) spec.setSteps(readObjectArray(node.get("steps")));
        if (node.has("actors")) spec.setActors(readStringArray(node.get("actors")));
        if (node.has("decisions")) spec.setDecisions(readObjectArray(node.get("decisions")));
        if (node.has("conditions")) spec.setConditions(readStringArray(node.get("conditions")));
        if (node.has("missingInfo")) spec.setMissingInfo(readStringArray(node.get("missingInfo")));
    }

    private List<java.util.Map<String, Object>> readObjectArray(JsonNode node) {
        try {
            return objectMapper.treeToValue(node,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, java.util.Map.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readStringArray(JsonNode node) {
        try {
            return objectMapper.treeToValue(node,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object toSpecJson(WorkflowSpec spec) {
        return objectMapper.valueToTree(java.util.Map.of(
                "processName", spec.getProcessName() != null ? spec.getProcessName() : "",
                "description", spec.getDescription() != null ? spec.getDescription() : "",
                "steps", spec.getSteps() != null ? spec.getSteps() : List.of(),
                "actors", spec.getActors() != null ? spec.getActors() : List.of(),
                "decisions", spec.getDecisions() != null ? spec.getDecisions() : List.of(),
                "conditions", spec.getConditions() != null ? spec.getConditions() : List.of(),
                "missingInfo", spec.getMissingInfo() != null ? spec.getMissingInfo() : List.of()
        ));
    }

    private String describeSpecBrief(WorkflowSpec spec) {
        return "processName=" + spec.getProcessName()
                + ", steps=" + spec.getSteps()
                + ", missingInfo=" + spec.getMissingInfo();
    }

    public String buildSpecText(WorkflowSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("Process: ").append(spec.getProcessName()).append('\n');
        if (spec.getDescription() != null) {
            sb.append("Description: ").append(spec.getDescription()).append('\n');
        }
        sb.append("Steps: ").append(spec.getSteps()).append('\n');
        sb.append("Actors/Roles: ").append(spec.getActors()).append('\n');
        sb.append("Decision points: ").append(spec.getDecisions()).append('\n');
        sb.append("Conditions: ").append(spec.getConditions()).append('\n');
        return sb.toString();
    }

    private String buildSummaryMessage(WorkflowSpec spec) {
        return "Here's what I've gathered so far:\n\n" + buildSpecText(spec);
    }

    private UUID parseUuid(String conversationId) {
        try {
            return conversationId != null ? UUID.fromString(conversationId) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
