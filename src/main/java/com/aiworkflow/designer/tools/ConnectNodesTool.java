package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ConnectNodesTool implements WorkflowTool {

    @Override
    public String getName() {
        return "connect_nodes";
    }

    @Override
    public String getDescription() {
        return "Connect two workflow nodes with a sequence flow";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing connect_nodes tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String sourceId = (String) parameters.get("sourceId");
        String targetId = (String) parameters.get("targetId");
        String condition = (String) parameters.get("condition");

        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Source node ID is required");
        }

        if (targetId == null || targetId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target node ID is required");
        }

        // Verify nodes exist and no duplicate flow is created
        GraphInvariants.requireEndpointNodes(workflow, sourceId, targetId);
        GraphInvariants.requireNoDuplicateEdge(workflow, sourceId, targetId);

        WorkflowEdge edge;
        if (condition != null && !condition.trim().isEmpty()) {
            edge = new WorkflowEdge(sourceId, targetId, condition);
        } else {
            edge = new WorkflowEdge(sourceId, targetId);
        }

        workflow.addEdge(edge);

        log.info("Connected nodes {} -> {}", sourceId, targetId);
        return Map.of(
                "success", true,
                "sourceId", sourceId,
                "targetId", targetId,
                "condition", condition != null ? condition : "none"
        );
    }
}
