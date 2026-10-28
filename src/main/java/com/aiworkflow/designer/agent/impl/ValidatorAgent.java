package com.aiworkflow.designer.agent.impl;

import com.aiworkflow.designer.agent.core.BaseAgent;
import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ValidatorAgent extends BaseAgent {

    public ValidatorAgent(ChatClient chatClient, ChatModel chatModel, KnowledgeService knowledgeService) {
        super(chatClient, chatModel, knowledgeService);
    }

    @Override
    public String getAgentType() {
        return "VALIDATOR";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are the Validator Agent for an AI-powered BPMN workflow designer.
            Your role is to validate BPMN workflows for structural, logical, and business correctness.

            Validation Categories:
            1. Structural Validation:
               - Must have exactly one start event
               - Must have at least one end event
               - All nodes must be reachable from start
               - No dead-end paths (except end events)
               - All gateways must have proper incoming/outgoing flows
               - No broken sequence flows

            2. Logical Validation:
               - All gateway conditions must be complete
               - No circular dependencies
               - No potential infinite loops
               - Conditional flows must have proper conditions
               - Default flows should be defined for exclusive gateways

            3. Business Validation:
               - No duplicate approvals
               - Error handling for critical paths
               - SLA compliance checks
               - Proper escalation paths
               - Missing timeout configurations

            Provide detailed feedback with:
            - Validation status (VALID, WARNING, ERROR)
            - Specific issues found
            - Suggestions for fixes
            - Best practice recommendations
            """;
    }

    @Override
    public AgentState process(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("ValidatorAgent processing request ({} chars)", userMessage.length());
        AgentState state = beginRequest();
        updateState("THINKING");

        try {
            state.addContext("user_message", userMessage);
            state.addContext("workflow", workflow);

            if (workflow == null) {
                state.setLastAgentResponse("No workflow provided for validation.");
                updateState("COMPLETED");
                return state;
            }

            updateState("EXECUTING");

            ValidationResult result = validateWorkflow(workflow);
            state.addContext("validation_result", result);

            String response = generateValidationResponse(result);
            state.setLastAgentResponse(response);

            updateState("COMPLETED");
            return state;

        } catch (Exception e) {
            log.error("Validator agent failed", e);
            state.updateStatus("FAILED");
            state.addContext("error", e.getMessage());
            state.setLastAgentResponse("Validation failed.");
            return state;
        } finally {
            endRequest();
        }
    }

    private ValidationResult validateWorkflow(WorkflowDefinition workflow) {
        log.info("Validating workflow: {}", workflow.getName());

        ValidationResult result = new ValidationResult();
        result.setWorkflowName(workflow.getName());

        // Structural validation
        validateStructure(workflow, result);

        // Logical validation
        validateLogic(workflow, result);

        // Business validation
        validateBusinessRules(workflow, result);

        // Determine overall status
        if (result.getErrors().isEmpty() && result.getWarnings().isEmpty()) {
            result.setStatus("VALID");
        } else if (!result.getErrors().isEmpty()) {
            result.setStatus("ERROR");
        } else {
            result.setStatus("WARNING");
        }

        log.info("Validation completed with status: {}", result.getStatus());
        return result;
    }

    private void validateStructure(WorkflowDefinition workflow, ValidationResult result) {
        log.debug("Validating structure");

        // Check for start event
        if (workflow.getStartEvent() == null) {
            result.addError("Missing start event");
        } else if (workflow.getNodesByType("START_EVENT").size() > 1) {
            result.addError("Multiple start events found (should have exactly one)");
        }

        // Check for end events
        if (workflow.getEndEvents().isEmpty()) {
            result.addError("Missing end event");
        }

        // Check for unreachable nodes
        List<String> reachableNodes = findReachableNodes(workflow);
        for (WorkflowNode node : workflow.getNodes()) {
            if (!reachableNodes.contains(node.getId())) {
                result.addError("Unreachable node: " + node.getName());
            }
        }

        // Check for dead-end paths
        for (WorkflowNode node : workflow.getNodes()) {
            if (!node.isEndEvent() && workflow.getEdgesFromNode(node.getId()).isEmpty()) {
                result.addError("Dead-end path at node: " + node.getName());
            }
        }

        // Check for broken flows
        for (WorkflowEdge edge : workflow.getEdges()) {
            if (workflow.getNodeById(edge.getSource()) == null) {
                result.addError("Broken flow: source node not found - " + edge.getSource());
            }
            if (workflow.getNodeById(edge.getTarget()) == null) {
                result.addError("Broken flow: target node not found - " + edge.getTarget());
            }
        }
    }

    private void validateLogic(WorkflowDefinition workflow, ValidationResult result) {
        log.debug("Validating logic");

        // Check gateway conditions
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isGateway()) {
                List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(node.getId());
                int conditionalFlows = 0;

                for (WorkflowEdge edge : outgoingEdges) {
                    if (edge.hasCondition()) {
                        conditionalFlows++;
                    }
                }

                if (conditionalFlows == 0 && outgoingEdges.size() > 1) {
                    result.addWarning("Gateway " + node.getName() + " has multiple outgoing flows but no conditions");
                }

                if (conditionalFlows < outgoingEdges.size() - 1) {
                    result.addWarning("Gateway " + node.getName() + " may have incomplete conditions");
                }
            }
        }

        // Check for circular dependencies
        if (hasCircularDependency(workflow)) {
            result.addError("Circular dependency detected in workflow");
        }
    }

    private void validateBusinessRules(WorkflowDefinition workflow, ValidationResult result) {
        log.debug("Validating business rules");

        // Check for duplicate approvals
        List<WorkflowNode> approvalTasks = workflow.getNodes().stream()
                .filter(node -> node.getName().toLowerCase().contains("approval"))
                .toList();

        if (approvalTasks.size() > 1) {
            result.addWarning("Multiple approval tasks detected - consider if this is intentional");
        }

        // Check for tasks without error handling
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isTask() && !hasErrorHandling(workflow, node)) {
                result.addWarning("Task " + node.getName() + " has no error handling");
            }
        }

        // Check for user tasks without assignee
        for (WorkflowNode node : workflow.getNodes()) {
            if ("USER_TASK".equals(node.getType())) {
                Object assignee = node.getProperty("assignee");
                if (assignee == null || assignee.toString().trim().isEmpty()) {
                    result.addWarning("User task " + node.getName() + " has no assignee defined");
                }
            }
        }
    }

    private List<String> findReachableNodes(WorkflowDefinition workflow) {
        List<String> reachable = new ArrayList<>();
        WorkflowNode startEvent = workflow.getStartEvent();

        if (startEvent != null) {
            traverseFromNode(workflow, startEvent.getId(), reachable, new ArrayList<>());
        }

        return reachable;
    }

    private void traverseFromNode(WorkflowDefinition workflow, String nodeId, List<String> reachable, List<String> visited) {
        if (visited.contains(nodeId)) {
            return; // Prevent infinite loops
        }

        visited.add(nodeId);
        reachable.add(nodeId);

        for (WorkflowEdge edge : workflow.getEdgesFromNode(nodeId)) {
            traverseFromNode(workflow, edge.getTarget(), reachable, visited);
        }
    }

    private boolean hasCircularDependency(WorkflowDefinition workflow) {
        // Simple cycle detection using DFS
        for (WorkflowNode node : workflow.getNodes()) {
            if (hasCycleFromNode(workflow, node.getId(), new ArrayList<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleFromNode(WorkflowDefinition workflow, String nodeId, List<String> visited) {
        if (visited.contains(nodeId)) {
            return true;
        }

        visited.add(nodeId);

        for (WorkflowEdge edge : workflow.getEdgesFromNode(nodeId)) {
            if (hasCycleFromNode(workflow, edge.getTarget(), new ArrayList<>(visited))) {
                return true;
            }
        }

        return false;
    }

    private boolean hasErrorHandling(WorkflowDefinition workflow, WorkflowNode node) {
        // Check if the node has any boundary events or error handling
        // For now, simple check - in production, would be more sophisticated
        return false;
    }

    private String generateValidationResponse(ValidationResult result) {
        StringBuilder response = new StringBuilder();

        response.append("Workflow Validation Results:\n");
        response.append("Status: ").append(result.getStatus()).append("\n\n");

        if (!result.getErrors().isEmpty()) {
            response.append("Errors:\n");
            for (String error : result.getErrors()) {
                response.append("  ❌ ").append(error).append("\n");
            }
            response.append("\n");
        }

        if (!result.getWarnings().isEmpty()) {
            response.append("Warnings:\n");
            for (String warning : result.getWarnings()) {
                response.append("  ⚠️ ").append(warning).append("\n");
            }
            response.append("\n");
        }

        if (!result.getSuggestions().isEmpty()) {
            response.append("Suggestions:\n");
            for (String suggestion : result.getSuggestions()) {
                response.append("  💡 ").append(suggestion).append("\n");
            }
        }

        if (result.getErrors().isEmpty() && result.getWarnings().isEmpty()) {
            response.append("✅ Workflow is valid and follows best practices!");
        }

        return response.toString();
    }

    private static class ValidationResult {
        private String workflowName;
        private String status;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();

        public String getWorkflowName() { return workflowName; }
        public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getSuggestions() { return suggestions; }

        public void addError(String error) { errors.add(error); }
        public void addWarning(String warning) { warnings.add(warning); }
        public void addSuggestion(String suggestion) { suggestions.add(suggestion); }
    }
}
