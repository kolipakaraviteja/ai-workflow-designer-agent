package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExplainWorkflowTool implements WorkflowTool {

    private static final Logger logger = LoggerFactory.getLogger(ExplainWorkflowTool.class);

    @Override
    public String getName() {
        return "explain_workflow";
    }

    @Override
    public String getDescription() {
        return "Generate a natural language explanation of the workflow structure and flow";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        logger.info("Executing explain_workflow tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        List<String> explanation = new ArrayList<>();
        
        explanation.add("Workflow Explanation:");
        explanation.add(String.format("Name: %s", workflow.getName()));
        explanation.add(String.format("Description: %s", workflow.getDescription()));
        explanation.add("");
        
        explanation.add("Workflow Flow:");
        
        // Find start events
        workflow.getNodes().stream()
                .filter(node -> node.getType().equals("START_EVENT"))
                .forEach(startEvent -> {
                    explanation.add(String.format("- Starts with: %s", startEvent.getName()));
                    
                    // Trace the flow from this start event
                    traceFlow(workflow, startEvent.getId(), explanation, 1);
                });
        
        explanation.add("");
        explanation.add("Key Components:");
        
        workflow.getNodes().forEach(node -> {
            String typeDescription = getNodeDescription(node.getType());
            explanation.add(String.format("- %s (%s): %s", node.getName(), typeDescription, node.getType()));
        });

        log.info("Workflow explanation generated");
        return Map.of(
                "success", true,
                "explanation", explanation,
                "workflowName", workflow.getName()
        );
    }

    private void traceFlow(WorkflowDefinition workflow, String nodeId, List<String> explanation, int depth) {
        String indent = "  ".repeat(depth);
        
        workflow.getEdges().stream()
                .filter(edge -> edge.getSource().equals(nodeId))
                .forEach(edge -> {
                    workflow.getNodes().stream()
                            .filter(node -> node.getId().equals(edge.getTarget()))
                            .findFirst()
                            .ifPresent(targetNode -> {
                                String conditionText = edge.getCondition() != null && !edge.getCondition().isEmpty()
                                        ? String.format(" [if %s]", edge.getCondition())
                                        : "";
                                explanation.add(String.format("%s→ %s%s", indent, targetNode.getName(), conditionText));
                                
                                // Continue tracing for non-end nodes
                                if (!targetNode.getType().equals("END_EVENT")) {
                                    traceFlow(workflow, targetNode.getId(), explanation, depth + 1);
                                }
                            });
                });
    }

    private String getNodeDescription(String type) {
        return switch (type) {
            case "START_EVENT" -> "Start Event";
            case "END_EVENT" -> "End Event";
            case "USER_TASK" -> "User Task";
            case "SERVICE_TASK" -> "Service Task";
            case "SCRIPT_TASK" -> "Script Task";
            case "EXCLUSIVE_GATEWAY" -> "Decision Gateway";
            case "PARALLEL_GATEWAY" -> "Parallel Gateway";
            case "INCLUSIVE_GATEWAY" -> "Inclusive Gateway";
            case "TIMER_EVENT" -> "Timer Event";
            case "MESSAGE_EVENT" -> "Message Event";
            case "ERROR_EVENT" -> "Error Event";
            default -> type;
        };
    }
}
