package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class GetWorkflowTool implements WorkflowTool {

    @Override
    public String getName() {
        return "get_workflow";
    }

    @Override
    public String getDescription() {
        return "Retrieve the current workflow definition and structure";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing get_workflow tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        log.info("Retrieved workflow: {} with {} nodes and {} edges", 
                workflow.getName(), workflow.getNodes().size(), workflow.getEdges().size());

        return Map.of(
                "success", true,
                "workflow", workflow,
                "name", workflow.getName(),
                "description", workflow.getDescription(),
                "version", workflow.getVersion(),
                "nodeCount", workflow.getNodes().size(),
                "edgeCount", workflow.getEdges().size(),
                "variableCount", workflow.getVariables() != null ? workflow.getVariables().size() : 0
        );
    }
}
