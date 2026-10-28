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
import java.util.List;

@Slf4j
@Component
public class OptimizerAgent extends BaseAgent {

    public OptimizerAgent(ChatClient chatClient, ChatModel chatModel, KnowledgeService knowledgeService) {
        super(chatClient, chatModel, knowledgeService);
    }

    @Override
    public String getAgentType() {
        return "OPTIMIZER";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are the Optimizer Agent for an AI-powered BPMN workflow designer.
            Your role is to analyze workflows and identify optimization opportunities.

            Optimization Categories:
            1. Performance Optimization:
               - Identify sequential tasks that can be parallelized
               - Find bottlenecks and long-running tasks
               - Suggest async processing opportunities
               - Reduce unnecessary human intervention

            2. Process Optimization:
               - Eliminate redundant steps
               - Simplify complex gateways
               - Reduce approval layers
               - Automate manual tasks

            3. Resource Optimization:
               - Balance workload across actors
               - Reduce SLA risks
               - Optimize task assignments
               - Improve resource utilization

            4. Error Handling Optimization:
               - Add missing error handling
               - Improve escalation paths
               - Add retry logic
               - Implement compensation

            Provide specific, actionable recommendations with:
            - Optimization type
            - Current problem
            - Suggested improvement
            - Expected benefit
            - Implementation complexity
            """;
    }

    @Override
    public AgentState process(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("OptimizerAgent processing request ({} chars)", userMessage.length());
        AgentState state = beginRequest();
        updateState("THINKING");

        try {
            state.addContext("user_message", userMessage);
            state.addContext("workflow", workflow);

            if (workflow == null) {
                state.setLastAgentResponse("No workflow provided for optimization analysis.");
                updateState("COMPLETED");
                return state;
            }

            updateState("EXECUTING");

            OptimizationResult result = analyzeWorkflow(workflow);
            state.addContext("optimization_result", result);

            String response = generateOptimizationResponse(result);
            state.setLastAgentResponse(response);

            updateState("COMPLETED");
            return state;

        } catch (Exception e) {
            log.error("Optimizer agent failed", e);
            state.updateStatus("FAILED");
            state.addContext("error", e.getMessage());
            state.setLastAgentResponse("Optimization analysis failed.");
            return state;
        } finally {
            endRequest();
        }
    }

    private OptimizationResult analyzeWorkflow(WorkflowDefinition workflow) {
        log.info("Analyzing workflow for optimization opportunities: {}", workflow.getName());

        OptimizationResult result = new OptimizationResult();
        result.setWorkflowName(workflow.getName());

        // Performance analysis
        analyzePerformance(workflow, result);

        // Process analysis
        analyzeProcess(workflow, result);

        // Resource analysis
        analyzeResources(workflow, result);

        // Error handling analysis
        analyzeErrorHandling(workflow, result);

        log.info("Optimization analysis completed with {} recommendations", result.getRecommendations().size());
        return result;
    }

    private void analyzePerformance(WorkflowDefinition workflow, OptimizationResult result) {
        log.debug("Analyzing performance");

        // Look for sequential tasks that could be parallelized
        List<WorkflowNode> sequentialTasks = findSequentialTasks(workflow);
        if (sequentialTasks.size() > 2) {
            result.addRecommendation(new OptimizationRecommendation(
                    "PERFORMANCE",
                    "Sequential tasks detected",
                    "Consider parallelizing: " + sequentialTasks.stream()
                            .map(WorkflowNode::getName)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""),
                    "Parallel execution can reduce total processing time by up to " + (sequentialTasks.size() - 1) * 100 + "%",
                    "MEDIUM"
            ));
        }

        // Check for long-running tasks without timeout
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isTask() && node.getProperty("timeout") == null) {
                result.addRecommendation(new OptimizationRecommendation(
                        "PERFORMANCE",
                        "Task without timeout: " + node.getName(),
                        "Add timeout configuration to prevent hanging processes",
                        "Improves reliability and resource management",
                        "LOW"
                ));
            }
        }
    }

    private void analyzeProcess(WorkflowDefinition workflow, OptimizationResult result) {
        log.debug("Analyzing process");

        // Check for redundant approvals
        List<WorkflowNode> approvalTasks = workflow.getNodes().stream()
                .filter(node -> node.getName().toLowerCase().contains("approval"))
                .toList();

        if (approvalTasks.size() > 2) {
            result.addRecommendation(new OptimizationRecommendation(
                    "PROCESS",
                    "Multiple approval layers",
                    "Review if all " + approvalTasks.size() + " approval steps are necessary",
                    "Reducing approval layers can speed up process significantly",
                    "MEDIUM"
            ));
        }

        // Check for complex gateways
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isGateway()) {
                List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(node.getId());
                if (outgoingEdges.size() > 4) {
                    result.addRecommendation(new OptimizationRecommendation(
                            "PROCESS",
                            "Complex gateway: " + node.getName(),
                            "Consider simplifying or splitting this gateway with " + outgoingEdges.size() + " outgoing paths",
                            "Simpler gateways are easier to maintain and less error-prone",
                            "HIGH"
                    ));
                }
            }
        }
    }

    private void analyzeResources(WorkflowDefinition workflow, OptimizationResult result) {
        log.debug("Analyzing resources");

        // Check for unassigned user tasks
        for (WorkflowNode node : workflow.getNodes()) {
            if ("USER_TASK".equals(node.getType())) {
                Object assignee = node.getProperty("assignee");
                if (assignee == null || assignee.toString().trim().isEmpty()) {
                    result.addRecommendation(new OptimizationRecommendation(
                            "RESOURCE",
                            "Unassigned task: " + node.getName(),
                            "Define assignee or candidate groups for this task",
                            "Ensures tasks are properly routed and processed",
                            "LOW"
                    ));
                }
            }
        }

        // Check for workload imbalance (simple heuristic)
        List<WorkflowNode> userTasks = workflow.getNodes().stream()
                .filter(node -> "USER_TASK".equals(node.getType()))
                .toList();

        if (userTasks.size() > 5) {
            result.addRecommendation(new OptimizationRecommendation(
                    "RESOURCE",
                    "High number of user tasks: " + userTasks.size(),
                    "Consider automating some tasks or distributing workload better",
                    "Reduces bottlenecks and improves throughput",
                    "MEDIUM"
            ));
        }
    }

    private void analyzeErrorHandling(WorkflowDefinition workflow, OptimizationResult result) {
        log.debug("Analyzing error handling");

        // Check for service tasks without error handling
        for (WorkflowNode node : workflow.getNodes()) {
            if ("SERVICE_TASK".equals(node.getType()) && !hasErrorHandling(workflow, node)) {
                result.addRecommendation(new OptimizationRecommendation(
                        "ERROR_HANDLING",
                        "Service task without error handling: " + node.getName(),
                        "Add boundary error event or try-catch logic",
                        "Improves reliability and provides better error recovery",
                        "MEDIUM"
                ));
            }
        }

        // Check for missing retry logic
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isTask() && node.getProperty("retryCount") == null) {
                result.addRecommendation(new OptimizationRecommendation(
                        "ERROR_HANDLING",
                        "Task without retry logic: " + node.getName(),
                        "Consider adding retry configuration for transient failures",
                        "Improves resilience and reduces manual intervention",
                        "LOW"
                ));
            }
        }
    }

    private List<WorkflowNode> findSequentialTasks(WorkflowDefinition workflow) {
        // Simple heuristic - find consecutive tasks without gateways
        List<WorkflowNode> sequentialTasks = new ArrayList<>();

        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isTask()) {
                List<WorkflowEdge> incomingEdges = workflow.getEdgesToNode(node.getId());
                List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(node.getId());

                // Check if surrounded by other tasks
                boolean surroundedByTasks = incomingEdges.stream()
                        .allMatch(edge -> {
                            WorkflowNode sourceNode = workflow.getNodeById(edge.getSource());
                            return sourceNode != null && sourceNode.isTask();
                        }) && outgoingEdges.stream()
                        .allMatch(edge -> {
                            WorkflowNode targetNode = workflow.getNodeById(edge.getTarget());
                            return targetNode != null && targetNode.isTask();
                        });

                if (surroundedByTasks) {
                    sequentialTasks.add(node);
                }
            }
        }

        return sequentialTasks;
    }

    private boolean hasErrorHandling(WorkflowDefinition workflow, WorkflowNode node) {
        // Check if the node has any boundary events or error handling
        // For now, simple check - in production, would be more sophisticated
        return false;
    }

    private String generateOptimizationResponse(OptimizationResult result) {
        StringBuilder response = new StringBuilder();

        response.append("Workflow Optimization Analysis:\n");
        response.append("Workflow: ").append(result.getWorkflowName()).append("\n");
        response.append("Recommendations found: ").append(result.getRecommendations().size()).append("\n\n");

        if (result.getRecommendations().isEmpty()) {
            response.append("✅ Workflow appears well-optimized. No major issues found.");
            return response.toString();
        }

        // Group by category
        result.getRecommendations().stream()
                .collect(java.util.stream.Collectors.groupingBy(OptimizationRecommendation::getCategory))
                .forEach((category, recommendations) -> {
                    response.append(category).append(":\n");
                    for (OptimizationRecommendation rec : recommendations) {
                        response.append("  • ").append(rec.getProblem()).append("\n");
                        response.append("    💡 ").append(rec.getSuggestion()).append("\n");
                        response.append("    📈 Benefit: ").append(rec.getBenefit()).append("\n");
                        response.append("    🔧 Complexity: ").append(rec.getComplexity()).append("\n\n");
                    }
                });

        return response.toString();
    }

    private static class OptimizationResult {
        private String workflowName;
        private List<OptimizationRecommendation> recommendations = new ArrayList<>();

        public String getWorkflowName() { return workflowName; }
        public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
        public List<OptimizationRecommendation> getRecommendations() { return recommendations; }

        public void addRecommendation(OptimizationRecommendation recommendation) {
            recommendations.add(recommendation);
        }
    }

    private static class OptimizationRecommendation {
        private String category;
        private String problem;
        private String suggestion;
        private String benefit;
        private String complexity;

        public OptimizationRecommendation(String category, String problem, String suggestion, String benefit, String complexity) {
            this.category = category;
            this.problem = problem;
            this.suggestion = suggestion;
            this.benefit = benefit;
            this.complexity = complexity;
        }

        public String getCategory() { return category; }
        public String getProblem() { return problem; }
        public String getSuggestion() { return suggestion; }
        public String getBenefit() { return benefit; }
        public String getComplexity() { return complexity; }
    }
}
