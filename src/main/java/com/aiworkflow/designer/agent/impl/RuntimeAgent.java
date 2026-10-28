package com.aiworkflow.designer.agent.impl;

import com.aiworkflow.designer.agent.core.BaseAgent;
import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.knowledge.service.KnowledgeService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RuntimeAgent extends BaseAgent {

    public RuntimeAgent(ChatClient chatClient, ChatModel chatModel, KnowledgeService knowledgeService) {
        super(chatClient, chatModel, knowledgeService);
    }

    @Override
    public String getAgentType() {
        return "RUNTIME";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are the Runtime Agent for an AI-powered BPMN workflow designer.
            Your role is to explain workflow execution, simulate what-if scenarios, and provide insights into running processes.

            Capabilities:
            1. Workflow Explanation:
               - Explain the workflow structure in natural language
               - Describe the execution path
               - Explain decision points and conditions
               - Identify potential bottlenecks

            2. What-If Simulation:
               - Simulate execution with specific input variables
               - Show which path would be taken
               - Explain gateway decisions
               - Identify possible failure points

            3. Execution Analysis:
               - Explain why a process took a certain path
               - Analyze execution logs
               - Identify performance issues
               - Suggest improvements based on execution data

            4. Troubleshooting:
               - Explain execution failures
               - Identify root causes
               - Suggest fixes
               - Recommend preventive measures

            Provide clear, natural language explanations with:
            - Step-by-step execution flow
            - Decision explanations
            - Potential issues
            - Recommendations
            """;
    }

    @Override
    public AgentState process(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("RuntimeAgent processing request ({} chars)", userMessage.length());
        AgentState state = beginRequest();
        updateState("THINKING");

        try {
            state.addContext("user_message", userMessage);
            state.addContext("workflow", workflow);

            if (workflow == null) {
                state.setLastAgentResponse("No workflow provided for runtime analysis.");
                updateState("COMPLETED");
                return state;
            }

            updateState("EXECUTING");

            String lowerMessage = userMessage.toLowerCase();

            if (lowerMessage.contains("explain") || lowerMessage.contains("how does")) {
                String explanation = explainWorkflow(workflow);
                state.setLastAgentResponse(explanation);
            } else if (lowerMessage.contains("what if") || lowerMessage.contains("simulate")) {
                Map<String, Object> simulationVariables = extractSimulationVariables(userMessage);
                String simulationResult = simulateWorkflow(workflow, simulationVariables);
                state.setLastAgentResponse(simulationResult);
            } else if (lowerMessage.contains("path") || lowerMessage.contains("route")) {
                String pathExplanation = explainExecutionPath(workflow);
                state.setLastAgentResponse(pathExplanation);
            } else {
                // Default to explanation
                String explanation = explainWorkflow(workflow);
                state.setLastAgentResponse(explanation);
            }

            updateState("COMPLETED");
            return state;

        } catch (Exception e) {
            log.error("Runtime agent failed", e);
            state.updateStatus("FAILED");
            state.addContext("error", e.getMessage());
            state.setLastAgentResponse("Runtime analysis failed.");
            return state;
        } finally {
            endRequest();
        }
    }

    private String explainWorkflow(WorkflowDefinition workflow) {
        log.info("Explaining workflow: {}", workflow.getName());

        StringBuilder explanation = new StringBuilder();

        explanation.append("Workflow Explanation: ").append(workflow.getName()).append("\n");
        explanation.append("Description: ").append(workflow.getDescription()).append("\n\n");

        explanation.append("Workflow Structure:\n");

        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes.isEmpty()) {
            explanation.append("  (No nodes defined)\n");
        } else {
            for (WorkflowNode node : nodes) {
                explanation.append("  ").append(formatNode(node)).append("\n");
            }
        }

        explanation.append("\nExecution Flow:\n");
        String flowExplanation = explainExecutionFlow(workflow);
        explanation.append(flowExplanation);

        explanation.append("\nKey Characteristics:\n");
        explanation.append("  • Total nodes: ").append(nodes.size()).append("\n");
        explanation.append("  • Tasks: ").append(workflow.getNodesByType("USER_TASK").size() +
                         workflow.getNodesByType("SERVICE_TASK").size()).append("\n");
        explanation.append("  • Gateways: ").append(workflow.getNodesByType("EXCLUSIVE_GATEWAY").size() +
                         workflow.getNodesByType("PARALLEL_GATEWAY").size()).append("\n");

        return explanation.toString();
    }

    private String explainExecutionFlow(WorkflowDefinition workflow) {
        StringBuilder flow = new StringBuilder();

        WorkflowNode startEvent = workflow.getStartEvent();
        if (startEvent == null) {
            flow.append("  ⚠️ No start event defined\n");
            return flow.toString();
        }

        List<String> visited = new ArrayList<>();
        explainFromNode(workflow, startEvent.getId(), flow, visited, 0);

        return flow.toString();
    }

    private void explainFromNode(WorkflowDefinition workflow, String nodeId, StringBuilder flow, List<String> visited, int depth) {
        if (visited.contains(nodeId) || depth > 20) {
            return; // Prevent infinite loops
        }

        visited.add(nodeId);
        WorkflowNode node = workflow.getNodeById(nodeId);
        if (node == null) return;

        String indent = "  ".repeat(depth);
        flow.append(indent).append("→ ").append(node.getName());

        if (node.isGateway()) {
            List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(nodeId);
            flow.append(" (").append(outgoingEdges.size()).append(" paths)\n");

            for (WorkflowEdge edge : outgoingEdges) {
                WorkflowNode targetNode = workflow.getNodeById(edge.getTarget());
                if (targetNode != null) {
                    flow.append(indent).append("  ");
                    if (edge.hasCondition()) {
                        flow.append("If ").append(edge.getCondition()).append(" → ");
                    } else {
                        flow.append("→ ");
                    }
                    flow.append(targetNode.getName()).append("\n");
                    explainFromNode(workflow, edge.getTarget(), flow, visited, depth + 1);
                }
            }
        } else {
            flow.append("\n");
            List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(nodeId);
            if (outgoingEdges.size() == 1) {
                explainFromNode(workflow, outgoingEdges.get(0).getTarget(), flow, visited, depth);
            } else if (outgoingEdges.size() > 1) {
                flow.append(indent).append("  (Multiple outgoing flows)\n");
                for (WorkflowEdge edge : outgoingEdges) {
                    explainFromNode(workflow, edge.getTarget(), flow, visited, depth + 1);
                }
            }
        }
    }

    private String simulateWorkflow(WorkflowDefinition workflow, Map<String, Object> variables) {
        log.info("Simulating workflow with variables: {}", variables);

        StringBuilder simulation = new StringBuilder();

        simulation.append("Workflow Simulation\n");
        simulation.append("Variables: ").append(variables).append("\n\n");

        simulation.append("Simulated Execution Path:\n");

        // Simple simulation - in production, this would be more sophisticated
        WorkflowNode startEvent = workflow.getStartEvent();
        if (startEvent == null) {
            simulation.append("  ⚠️ Cannot simulate - no start event\n");
            return simulation.toString();
        }

        List<String> visited = new ArrayList<>();
        simulateFromNode(workflow, startEvent.getId(), simulation, visited, variables, 0);

        return simulation.toString();
    }

    private void simulateFromNode(WorkflowDefinition workflow, String nodeId, StringBuilder simulation,
                                   List<String> visited, Map<String, Object> variables, int depth) {
        if (visited.contains(nodeId) || depth > 20) {
            return;
        }

        visited.add(nodeId);
        WorkflowNode node = workflow.getNodeById(nodeId);
        if (node == null) return;

        String indent = "  ".repeat(depth);
        simulation.append(indent).append("✓ ").append(node.getName());

        if (node.isGateway()) {
            List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(nodeId);
            simulation.append(" (Evaluating ").append(outgoingEdges.size()).append(" conditions)\n");

            // Evaluate conditions
            WorkflowEdge selectedEdge = evaluateGatewayConditions(outgoingEdges, variables);
            if (selectedEdge != null) {
                simulation.append(indent).append("  → Selected: ").append(selectedEdge.getCondition() != null ?
                        selectedEdge.getCondition() : "Default path").append("\n");
                simulateFromNode(workflow, selectedEdge.getTarget(), simulation, visited, variables, depth + 1);
            } else {
                simulation.append(indent).append("  ⚠️ No matching condition found\n");
            }
        } else {
            simulation.append("\n");
            List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(nodeId);
            if (outgoingEdges.size() == 1) {
                simulateFromNode(workflow, outgoingEdges.get(0).getTarget(), simulation, visited, variables, depth);
            }
        }
    }

    private WorkflowEdge evaluateGatewayConditions(List<WorkflowEdge> edges, Map<String, Object> variables) {
        // Simple condition evaluation - in production, this would use a proper expression language
        for (WorkflowEdge edge : edges) {
            if (edge.isDefaultFlow()) {
                return edge;
            }
            if (edge.hasCondition()) {
                // For now, just return the first conditional edge
                // In production, would evaluate the actual condition
                return edge;
            }
        }
        return edges.isEmpty() ? null : edges.get(0);
    }

    private String explainExecutionPath(WorkflowDefinition workflow) {
        log.info("Explaining execution path for workflow: {}", workflow.getName());

        StringBuilder path = new StringBuilder();

        path.append("Execution Path Analysis\n\n");

        WorkflowNode startEvent = workflow.getStartEvent();
        if (startEvent == null) {
            path.append("⚠️ No start event defined\n");
            return path.toString();
        }

        path.append("Possible execution paths:\n");

        List<List<String>> paths = findAllPaths(workflow);
        for (int i = 0; i < paths.size(); i++) {
            path.append("Path ").append(i + 1).append(": ");
            path.append(paths.get(i).stream()
                    .map(nodeId -> {
                        WorkflowNode node = workflow.getNodeById(nodeId);
                        return node != null ? node.getName() : nodeId;
                    })
                    .reduce((a, b) -> a + " → " + b)
                    .orElse("Empty path"));
            path.append("\n");
        }

        return path.toString();
    }

    private List<List<String>> findAllPaths(WorkflowDefinition workflow) {
        List<List<String>> paths = new ArrayList<>();
        WorkflowNode startEvent = workflow.getStartEvent();

        if (startEvent != null) {
            List<String> currentPath = new ArrayList<>();
            findPathsFromNode(workflow, startEvent.getId(), currentPath, paths, new ArrayList<>());
        }

        return paths;
    }

    private void findPathsFromNode(WorkflowDefinition workflow, String nodeId, List<String> currentPath,
                                   List<List<String>> allPaths, List<String> visited) {
        if (visited.contains(nodeId)) {
            return;
        }

        visited.add(nodeId);
        currentPath.add(nodeId);

        WorkflowNode node = workflow.getNodeById(nodeId);
        if (node == null) return;

        if (node.isEndEvent()) {
            allPaths.add(new ArrayList<>(currentPath));
            currentPath.remove(currentPath.size() - 1);
            return;
        }

        List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(nodeId);
        if (outgoingEdges.isEmpty()) {
            // Dead end - still record as a path
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (WorkflowEdge edge : outgoingEdges) {
                findPathsFromNode(workflow, edge.getTarget(), new ArrayList<>(currentPath), allPaths, new ArrayList<>(visited));
            }
        }
    }

    private Map<String, Object> extractSimulationVariables(String message) {
        // Simple extraction - in production, would use AI to parse
        Map<String, Object> variables = new HashMap<>();

        // Look for patterns like "amount=1000" or "status='approved'"
        String[] parts = message.split("[,\\s]+");
        for (String part : parts) {
            if (part.contains("=")) {
                String[] keyValue = part.split("=");
                if (keyValue.length == 2) {
                    variables.put(keyValue[0].trim(), keyValue[1].trim().replace("'", ""));
                }
            }
        }

        return variables;
    }

    private String formatNode(WorkflowNode node) {
        StringBuilder formatted = new StringBuilder();

        formatted.append(node.getName()).append(" [").append(node.getType()).append("]");

        if (node.isGateway()) {
            List<String> details = new ArrayList<>();
            if (node.getProperty("defaultFlow") != null) {
                details.add("default: " + node.getProperty("defaultFlow"));
            }
            if (!details.isEmpty()) {
                formatted.append(" (").append(String.join(", ", details)).append(")");
            }
        } else if (node.isTask()) {
            List<String> details = new ArrayList<>();
            if (node.getProperty("assignee") != null) {
                details.add("assignee: " + node.getProperty("assignee"));
            }
            if (node.getProperty("async") != null) {
                details.add("async: " + node.getProperty("async"));
            }
            if (!details.isEmpty()) {
                formatted.append(" (").append(String.join(", ", details)).append(")");
            }
        }

        return formatted.toString();
    }
}
