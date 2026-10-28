package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sets or replaces the condition on an existing sequence flow. To create a
 * new conditional flow between nodes that are not connected yet, use
 * connect_nodes with a condition instead.
 */
@Slf4j
@Component
public class AddConditionTool implements WorkflowTool {

    @Override
    public String getName() {
        return "add_condition";
    }

    @Override
    public String getDescription() {
        return "Set or replace the condition on an existing sequence flow, addressed "
                + "by edgeId or by sourceId+targetId. Does not create new flows.";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing add_condition tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String edgeId = (String) parameters.get("edgeId");
        String sourceId = (String) parameters.get("sourceId");
        String targetId = (String) parameters.get("targetId");
        String condition = (String) parameters.get("condition");

        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("condition is required");
        }

        WorkflowEdge edge = resolveEdge(workflow, edgeId, sourceId, targetId);
        if (edge == null) {
            throw new IllegalArgumentException(
                    "No existing sequence flow found for " + describeAddress(edgeId, sourceId, targetId)
                            + " — use connect_nodes with a condition to create a new flow");
        }

        String previous = edge.getCondition();
        edge.setCondition(condition.trim());

        log.info("Set condition on edge {} ({} -> {})", edge.getId(), edge.getSource(), edge.getTarget());
        return Map.of(
                "success", true,
                "edgeId", edge.getId(),
                "sourceId", edge.getSource(),
                "targetId", edge.getTarget(),
                "condition", edge.getCondition(),
                "previousCondition", previous != null ? previous : "none"
        );
    }

    private WorkflowEdge resolveEdge(WorkflowDefinition workflow,
                                     String edgeId, String sourceId, String targetId) {
        if (edgeId != null && !edgeId.isBlank()) {
            return workflow.getEdges().stream()
                    .filter(e -> edgeId.equals(e.getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (sourceId != null && targetId != null) {
            return workflow.getEdges().stream()
                    .filter(e -> e.getSource().equals(sourceId) && e.getTarget().equals(targetId))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String describeAddress(String edgeId, String sourceId, String targetId) {
        if (edgeId != null && !edgeId.isBlank()) {
            return "edgeId=" + edgeId;
        }
        return "sourceId=" + sourceId + ", targetId=" + targetId;
    }
}
