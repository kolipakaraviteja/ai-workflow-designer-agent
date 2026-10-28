package com.aiworkflow.designer.workflow.service;

import java.util.List;

/**
 * Thrown when an agent-produced or API-submitted workflow fails mandatory
 * validation. Carries the validation error list so callers can surface it.
 */
public class WorkflowValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public WorkflowValidationException(List<String> validationErrors) {
        super("Workflow failed validation: " + String.join("; ", validationErrors));
        this.validationErrors = validationErrors;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
