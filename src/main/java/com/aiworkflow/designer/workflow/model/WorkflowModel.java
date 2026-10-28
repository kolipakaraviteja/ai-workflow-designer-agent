package com.aiworkflow.designer.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowModel {

    private WorkflowDefinition definition;
    private Map<String, Object> executionContext;
    private Map<String, Object> globalVariables;
    private Map<String, Object> processVariables;

    public WorkflowModel(WorkflowDefinition definition) {
        this.definition = definition;
        this.executionContext = new java.util.HashMap<>();
        this.globalVariables = new java.util.HashMap<>();
        this.processVariables = new java.util.HashMap<>();
    }

    public void setGlobalVariable(String key, Object value) {
        if (this.globalVariables == null) {
            this.globalVariables = new java.util.HashMap<>();
        }
        this.globalVariables.put(key, value);
    }

    public Object getGlobalVariable(String key) {
        return globalVariables != null ? globalVariables.get(key) : null;
    }

    public void setProcessVariable(String key, Object value) {
        if (this.processVariables == null) {
            this.processVariables = new java.util.HashMap<>();
        }
        this.processVariables.put(key, value);
    }

    public Object getProcessVariable(String key) {
        return processVariables != null ? processVariables.get(key) : null;
    }
}
