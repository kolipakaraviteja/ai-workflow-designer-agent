package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OptimizeWorkflowTool implements WorkflowTool {

    @Override
    public String getName() {
        return "optimize_workflow";
    }

    @Override
    public String getDescription() {
        return "Analyze workflow and suggest optimizations for performance and efficiency";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing optimize_workflow tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        List<String> suggestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        suggestions.add("Workflow Optimization Analysis:");
        
        // Analyze task distribution
        long userTasks = workflow.getNodes().stream().filter(n -> n.getType().equals("USER_TASK")).count();
        long serviceTasks = workflow.getNodes().stream().filter(n -> n.getType().equals("SERVICE_TASK")).count();
        long totalTasks = userTasks + serviceTasks;
        
        if (totalTasks > 0) {
            double automationRatio = (double) serviceTasks / totalTasks;
            suggestions.add(String.format("- Automation ratio: %.1f%% (%d service tasks out of %d total tasks)",
                    automationRatio * 100, serviceTasks, totalTasks));
            
            if (automationRatio < 0.3) {
                suggestions.add("  Recommendation: Consider automating more user tasks to improve efficiency");
                warnings.add("Low automation ratio may cause process delays");
            }
        }
        
        // Analyze gateway complexity
        long gateways = workflow.getNodes().stream().filter(n -> n.getType().contains("GATEWAY")).count();
        if (gateways > 5) {
            suggestions.add(String.format("- High gateway complexity: %d gateways detected", gateways));
            suggestions.add("  Recommendation: Simplify decision logic or combine related gateways");
            warnings.add("Complex gateway structures may be difficult to maintain");
        }
        
        // Analyze potential bottlenecks
        workflow.getNodes().stream()
                .filter(n -> n.getType().equals("USER_TASK"))
                .forEach(task -> {
                    if (task.getProperty("assignee") == null) {
                        suggestions.add(String.format("- Task '%s' has no assignee defined", task.getName()));
                        warnings.add("Unassigned tasks may cause process delays");
                    }
                });
        
        // Analyze sequential vs parallel execution
        long parallelGateways = workflow.getNodes().stream()
                .filter(n -> n.getType().equals("PARALLEL_GATEWAY"))
                .count();
        
        if (parallelGateways == 0 && totalTasks > 3) {
            suggestions.add("- No parallel execution detected");
            suggestions.add("  Recommendation: Consider using parallel gateways for independent tasks to reduce total execution time");
        }
        
        // Analyze error handling
        long errorEvents = workflow.getNodes().stream()
                .filter(n -> n.getType().equals("ERROR_EVENT"))
                .count();
        
        if (errorEvents == 0 && serviceTasks > 0) {
            suggestions.add("- No error handling events detected");
            suggestions.add("  Recommendation: Add error events to handle service task failures gracefully");
        }
        
        // Analyze workflow length
        if (workflow.getNodes().size() > 20) {
            suggestions.add(String.format("- Workflow is complex with %d nodes", workflow.getNodes().size()));
            suggestions.add("  Recommendation: Consider breaking into subprocesses for better maintainability");
        }
        
        // Check for orphaned nodes
        List<String> connectedNodeIds = new ArrayList<>();
        workflow.getEdges().forEach(edge -> {
            connectedNodeIds.add(edge.getSource());
            connectedNodeIds.add(edge.getTarget());
        });
        
        long orphanedNodes = workflow.getNodes().stream()
                .filter(node -> !connectedNodeIds.contains(node.getId()))
                .count();
        
        if (orphanedNodes > 0) {
            suggestions.add(String.format("- Found %d orphaned nodes not connected to the workflow", orphanedNodes));
            warnings.add("Orphaned nodes should be connected or removed");
        }

        log.info("Workflow optimization analysis completed");
        return Map.of(
                "success", true,
                "suggestions", suggestions,
                "warnings", warnings,
                "automationRatio", totalTasks > 0 ? (double) serviceTasks / totalTasks : 0.0,
                "complexityScore", calculateComplexityScore(workflow)
        );
    }

    private double calculateComplexityScore(WorkflowDefinition workflow) {
        int nodeCount = workflow.getNodes().size();
        int edgeCount = workflow.getEdges().size();
        long gatewayCount = workflow.getNodes().stream().filter(n -> n.getType().contains("GATEWAY")).count();
        
        // Simple complexity score based on nodes, edges, and gateways
        double baseScore = nodeCount * 1.0;
        double edgeScore = edgeCount * 0.5;
        double gatewayScore = gatewayCount * 2.0;
        
        return baseScore + edgeScore + gatewayScore;
    }
}
