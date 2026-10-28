package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class UpdateWorkflowTool implements WorkflowTool {

    @Override
    public String getName() {
        return "update_workflow";
    }

    @Override
    public String getDescription() {
        return "Update workflow metadata (name, description, variables)";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing update_workflow tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String name = (String) parameters.get("name");
        String description = (String) parameters.get("description");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) parameters.get("variables");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parameters.get("metadata");

        if (name != null && !name.trim().isEmpty()) {
            workflow.setName(name);
            log.info("Updated workflow name to: {}", name);
        }

        if (description != null && !description.trim().isEmpty()) {
            workflow.setDescription(description);
            log.info("Updated workflow description");
        }

        if (variables != null) {
            workflow.setVariables(variables);
            log.info("Updated workflow variables");
        }

        if (metadata != null) {
            workflow.setMetadata(metadata);
            log.info("Updated workflow metadata");
        }

        return Map.of(
                "success", true,
                "workflowName", workflow.getName(),
                "workflowDescription", workflow.getDescription(),
                "variableCount", workflow.getVariables() != null ? workflow.getVariables().size() : 0
        );
    }
}
