package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.bpmn.BpmnValidator;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent-facing validation tool — delegates to the single {@link BpmnValidator}
 * service so tool results and API results can never disagree.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateWorkflowTool implements WorkflowTool {

    private final BpmnValidator bpmnValidator;

    @Override
    public String getName() {
        return "validate_workflow";
    }

    @Override
    public String getDescription() {
        return "Validate the workflow structure, logic and business rules";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing validate_workflow tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        BpmnValidator.ValidationResult result = bpmnValidator.validate(workflow);

        log.info("Validation completed. Status: {}, Errors: {}, Warnings: {}",
                result.getStatus(), result.getErrors().size(), result.getWarnings().size());

        return result;
    }
}
