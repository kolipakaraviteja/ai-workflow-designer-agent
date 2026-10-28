package com.aiworkflow.designer.agent.impl;

import com.aiworkflow.designer.agent.core.BaseAgent;
import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class OrchestratorAgent extends BaseAgent {

    private final Map<String, BaseAgent> agentRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrchestratorAgent(ChatClient chatClient, ChatModel chatModel, KnowledgeService knowledgeService) {
        super(chatClient, chatModel, knowledgeService);
        this.agentRegistry = new HashMap<>();
    }

    public void registerAgent(BaseAgent agent) {
        agentRegistry.put(agent.getAgentType(), agent);
        log.info("Registered agent: {}", agent.getAgentType());
    }

    @Override
    public String getAgentType() {
        return "ORCHESTRATOR";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are the Orchestrator Agent for an AI-powered BPMN workflow designer.
            Your role is to analyze user requests and route them to the appropriate specialized agent.

            Available agents:
            - CONSULTANT: Conversational requirements gathering - clarifies what the client wants,
              collects process details, and confirms a spec before building (DEFAULT for new requests)
            - DESIGNER: Creates and modifies workflow structures - only for confirmed specs or
              concrete modification requests on an existing workflow
            - VALIDATOR: Validates BPMN structure, logic, and business rules
            - OPTIMIZER: Analyzes workflows for optimization opportunities
            - RUNTIME: Explains running processes and execution failures

            Rules:
            - General conversation, greetings, questions, and NEW workflow requests -> CONSULTANT.
            - Only route to DESIGNER when the user is confirming a gathered spec, or giving a
              concrete modification to an existing workflow (e.g. "add an approval task").
            """;
    }

    @Override
    public AgentState process(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("Orchestrator processing request ({} chars)", userMessage.length());
        AgentState state = beginRequest();
        updateState("THINKING");

        try {
            state.addContext("user_message", userMessage);
            state.addContext("workflow", workflow);

            // Internal planning call - null conversationId keeps it out of persisted chat history
            String orchestrationPlan = generateOrchestrationPlan(userMessage, workflow, null);
            state.addContext("orchestration_plan", orchestrationPlan);

            updateState("EXECUTING");

            AgentState result = executeOrchestrationPlan(orchestrationPlan, userMessage, workflow, conversationId);

            // Consultant confirmed a spec -> hand off to Designer within this request
            if (Boolean.TRUE.equals(result.getContext("ready_to_build"))) {
                result = handOffToDesigner(result, workflow, conversationId);
            }

            updateState("COMPLETED");
            return result;

        } catch (Exception e) {
            log.error("Orchestration failed", e);
            state.updateStatus("FAILED");
            state.addContext("error", e.getMessage());
            return state;
        } finally {
            endRequest();
        }
    }

    private String generateOrchestrationPlan(String userMessage, WorkflowDefinition workflow, String conversationId) {
        String context = String.format("""
            Current workflow: %s
            User request: %s
            Available agents: %s
            """,
            workflow != null ? workflow.getName() : "None",
            userMessage,
            String.join(", ", agentRegistry.keySet())
        );

        return generateResponseWithContext(
            "Analyze this request and create an orchestration plan. Return a JSON object with keys agent_type, action and priority.",
            getSystemPrompt(),
            context,
            conversationId
        );
    }

    private AgentState executeOrchestrationPlan(String orchestrationPlan, String userMessage,
                                                WorkflowDefinition workflow, String conversationId) {
        String plannedAgent = parsePlannedAgent(orchestrationPlan);
        if (plannedAgent != null && agentRegistry.containsKey(plannedAgent)) {
            log.info("Routing to {} agent (from orchestration plan)", plannedAgent);
            return agentRegistry.get(plannedAgent).process(userMessage, workflow, conversationId);
        }

        // Fallback: heuristic routing when the plan is missing or unparsable
        String lowerMessage = userMessage.toLowerCase();
        boolean hasExistingWorkflow = workflow != null && !workflow.getNodes().isEmpty();

        if (hasExistingWorkflow && (lowerMessage.contains("add") || lowerMessage.contains("remove")
                || lowerMessage.contains("modify") || lowerMessage.contains("move")
                || lowerMessage.contains("rename"))) {
            return route("DESIGNER", userMessage, workflow, conversationId);
        }

        if (lowerMessage.contains("validate") || lowerMessage.contains("check") ||
            lowerMessage.contains("error") || lowerMessage.contains("issue")) {
            return route("VALIDATOR", userMessage, workflow, conversationId);
        }

        if (lowerMessage.contains("optimize") || lowerMessage.contains("improve") ||
            lowerMessage.contains("bottleneck") || lowerMessage.contains("parallel")) {
            return route("OPTIMIZER", userMessage, workflow, conversationId);
        }

        if (lowerMessage.contains("explain") || lowerMessage.contains("what happens") ||
            lowerMessage.contains("simulate") || lowerMessage.contains("execution")) {
            return route("RUNTIME", userMessage, workflow, conversationId);
        }

        // Default: conversational intake - never build from an ambiguous request
        return route("CONSULTANT", userMessage, workflow, conversationId);
    }

    private AgentState route(String agentType, String userMessage,
                             WorkflowDefinition workflow, String conversationId) {
        BaseAgent agent = agentRegistry.get(agentType);
        if (agent != null) {
            log.info("Routing to {} agent", agentType);
            return agent.process(userMessage, workflow, conversationId);
        }

        log.warn("No suitable agent found ({}), falling back to consultant", agentType);
        BaseAgent consultant = agentRegistry.get("CONSULTANT");
        if (consultant != null) {
            return consultant.process(userMessage, workflow, conversationId);
        }

        AgentState state = currentState();
        state.setLastAgentResponse("I couldn't determine how to help with that. Could you be more specific?");
        return state;
    }

    /**
     * Runs the Designer agent against the confirmed spec and merges the consultant's
     * spec context into the designer's result so the controller can see it.
     */
    private AgentState handOffToDesigner(AgentState consultantResult, WorkflowDefinition workflow,
                                         String conversationId) {
        String specText = (String) consultantResult.getContext("spec_summary");
        if (specText == null) {
            specText = "Build the workflow the client confirmed in this conversation.";
        }

        BaseAgent designer = agentRegistry.get("DESIGNER");
        if (designer == null) {
            return consultantResult;
        }

        log.info("Spec confirmed - handing off to DESIGNER");
        // null conversationId: the hand-off prompt is internal and must not be persisted
        AgentState designerResult = designer.process(
                "Create a BPMN workflow from this confirmed specification:\n\n" + specText,
                workflow, null);

        designerResult.addContext("spec", consultantResult.getContext("spec"));
        designerResult.addContext("spec_confirmed", true);
        return designerResult;
    }

    /**
     * Extracts the agent_type value from the LLM's JSON orchestration plan.
     * Returns null when no usable plan is found.
     */
    private String parsePlannedAgent(String orchestrationPlan) {
        if (orchestrationPlan == null) {
            return null;
        }
        try {
            String json = firstJsonObject(orchestrationPlan);
            if (json == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            JsonNode agentType = node.has("agent_type") ? node.get("agent_type")
                    : node.has("agent") ? node.get("agent") : null;
            if (agentType == null || !agentType.isTextual()) {
                return null;
            }
            return normalizeAgentType(agentType.asText().trim().toUpperCase());
        } catch (Exception e) {
            log.debug("Could not parse orchestration plan: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the first complete JSON object from the model output: strips
     * markdown fences, then scans for matching braces so a trailing code block
     * or commentary cannot corrupt the payload.
     */
    private String firstJsonObject(String raw) {
        String cleaned = raw.replaceAll("(?s)```(?:json)?", "").trim();
        int start = cleaned.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
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
            else if (c == '}' && --depth == 0) {
                return cleaned.substring(start, i + 1);
            }
        }
        return null;
    }

    private String normalizeAgentType(String type) {
        return switch (type) {
            case "ASSISTANT", "INTAKE", "REQUIREMENTS", "GATHERER" -> "CONSULTANT";
            case "BUILDER", "GENERATOR" -> "DESIGNER";
            default -> type;
        };
    }
}
