package com.aiworkflow.designer.agent.impl;

import com.aiworkflow.designer.agent.core.BaseAgent;
import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.tools.WorkflowToolCallbacks;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class DesignerAgent extends BaseAgent {

    private final WorkflowToolCallbacks toolCallbacks;
    private final ObjectMapper objectMapper;

    public DesignerAgent(ChatClient chatClient, ChatModel chatModel,
                         WorkflowToolCallbacks toolCallbacks, ObjectMapper objectMapper,
                         KnowledgeService knowledgeService) {
        super(chatClient, chatModel, knowledgeService);
        this.toolCallbacks = toolCallbacks;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAgentType() {
        return "DESIGNER";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are the Designer Agent for an AI-powered BPMN workflow designer.
            Your role is to create and modify workflow structures based on natural language descriptions.

            You have tools available to manipulate the workflow. ALWAYS use tools to make
            changes - never just describe them:

            - create_workflow: create a new workflow (use only when no workflow exists)
            - add_task: add USER_TASK, SERVICE_TASK or SCRIPT_TASK nodes
            - update_task: change an existing task's type, name, assignee or implementation
              IN PLACE (keeps its id and all flows). ALWAYS prefer this over removing
              and recreating a task when the user asks to modify a task.
            - add_gateway: add EXCLUSIVE_GATEWAY (XOR decision), PARALLEL_GATEWAY (AND split/join), INCLUSIVE_GATEWAY (OR)
            - add_event: add START_EVENT, END_EVENT, TIMER_EVENT, MESSAGE_EVENT, ERROR_EVENT
            - connect_nodes: create a sequence flow between two node ids, optionally with a condition
            - add_condition: set or replace the condition on an EXISTING flow (by edgeId or sourceId+targetId)
            - remove_task: remove a task by id (only for simple chains or detached tasks)
            - update_workflow: rename the workflow or change its description/variables
            - validate_workflow: check the current workflow for problems
            - get_workflow / explain_workflow: inspect the current workflow
            - generate_bpmn_xml: produce BPMN 2.0 XML

            Rules:
            1. Every workflow must contain exactly one START_EVENT and at least one END_EVENT.
            2. Every node must be connected; a process flows start -> tasks/gateways -> end.
               You MUST connect_nodes from the start event to the first step, and connect
               every terminal path to an end event — unconnected nodes are errors.
            3. When creating a workflow from a description: call create_workflow, then
               add_event (start), add each task, add gateways for decisions, add_event (end),
               then connect_nodes for each sequence flow. Give every node a stable,
               lowercase id like "task_review_application".
            4. USER_TASKs must have an assignee, candidateUsers, or candidateGroups
               (e.g. candidateGroups="managers"). SERVICE_TASKs must have an
               implementation: serviceClass, expression or delegateExpression
               (e.g. expression="${leaveService.checkBalance(execution)}"). Set these
               in add_task or afterwards with update_task.
            5. To put a condition on a flow that already exists, use add_condition —
               connect_nodes to an already-connected pair will fail.
            6. After modifying a workflow, ALWAYS call validate_workflow and fix every
               reported ERROR before finishing (warnings are optional). Keep fixing
               until validation reports no errors.
            7. Answer the user with a short summary of what you changed.
            """;
    }

    @Override
    public AgentState process(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("DesignerAgent processing request ({} chars)", userMessage.length());
        AgentState state = beginRequest();
        updateState("THINKING");

        try {
            state.addContext("user_message", userMessage);
            state.addContext("workflow", workflow);
            updateState("EXECUTING");

            AtomicReference<WorkflowDefinition> workflowRef = new AtomicReference<>(workflow);
            AtomicBoolean mutated = new AtomicBoolean(false);

            FunctionCallback[] callbacks = toolCallbacks.build(
                    toolCallbacks.designToolNames(), workflowRef, mutated);

            String ragContext = knowledgeService.retrieveContext(userMessage, 4);
            var spec = chatClient.prompt()
                    .system(getSystemPrompt()
                            + (ragContext.isEmpty() ? "" : RAG_GUARD + "\n" + ragContext)
                            + "\n\nCurrent workflow:\n" + describeWorkflow(workflow))
                    .user(userMessage)
                    .functions(callbacks);
            spec = withConversationId(spec, conversationId);

            String response = callLlm(spec);

            WorkflowDefinition resultWorkflow = workflowRef.get();
            if (mutated.get() && resultWorkflow != null) {
                state.addContext("modified_workflow", resultWorkflow);
            }

            state.setLastAgentResponse(response != null ? response : "Done.");
            updateState("COMPLETED");
            return state;

        } catch (Exception e) {
            log.error("Designer agent failed", e);
            state.updateStatus("FAILED");
            state.addContext("error", e.getMessage());
            state.setLastAgentResponse("I encountered an error while processing your request.");
            return state;
        } finally {
            endRequest();
        }
    }

    private String describeWorkflow(WorkflowDefinition workflow) {
        if (workflow == null || workflow.getNodes().isEmpty()) {
            return "No workflow exists yet.";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);
        } catch (Exception e) {
            return workflow.getName();
        }
    }
}
