package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CreateWorkflowTool implements WorkflowTool {

    @Override
    public String getName() {
        return "create_workflow";
    }

    @Override
    public String getDescription() {
        return "Create a new workflow with the given name and description. Refused "
                + "when a non-empty workflow already exists unless forceReplace=true "
                + "is passed explicitly.";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing create_workflow tool with parameters: {}", parameters);

        String name = (String) parameters.get("name");
        String description = (String) parameters.get("description");
        boolean forceReplace = Boolean.TRUE.equals(parameters.get("forceReplace"))
                || "true".equalsIgnoreCase(String.valueOf(parameters.get("forceReplace")));

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow name is required");
        }

        // A prompt rule is not a safety boundary — enforce the precondition here
        // so an accidental call during an edit cannot discard the loaded graph.
        if (workflow != null && workflow.getNodes() != null && !workflow.getNodes().isEmpty()
                && !forceReplace) {
            throw new IllegalArgumentException(
                    "A workflow named '" + workflow.getName() + "' with "
                            + workflow.getNodes().size() + " nodes already exists. "
                            + "Refusing to replace it — use update_task/add_task/connect_nodes to edit it, "
                            + "or pass forceReplace=true only if the user explicitly asked to start over.");
        }

        WorkflowDefinition newWorkflow = new WorkflowDefinition(name, description);

        log.info("Created new workflow: {}", newWorkflow.getName());
        return newWorkflow;
    }
}
