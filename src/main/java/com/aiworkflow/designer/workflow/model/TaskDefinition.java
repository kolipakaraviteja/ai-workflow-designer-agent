package com.aiworkflow.designer.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class TaskDefinition {

    private String assignee;
    private String candidateUsers;
    private String candidateGroups;
    private String dueDate;
    private Integer priority;
    private String formKey;
    private String listener;
    private Map<String, Object> taskVariables;
    private Boolean async;
    private Boolean exclusive;
    private String skipExpression;
    private String dueDateExpression;

    // Service Task specific
    private String serviceImplementation; // CLASS, DELEGATE_EXPRESSION, EXPRESSION
    private String serviceClass;
    private String delegateExpression;
    private String expression;
    private String resultVariableName;

    // Script Task specific
    private String scriptFormat;
    private String script;

    // Business Rule Task specific
    private String ruleVariablesInput;
    private String ruleResultVariable;
    private Boolean exclude;

    // Send Task specific
    private String messageType;
    private String messageExpression;

    // Receive Task specific
    private String messageTypeToReceive;
    private String messageVariable;

    public TaskDefinition() {
        this.priority = 50; // Default priority
        this.async = false;
        this.exclusive = true;
        this.exclude = false;
        this.taskVariables = new java.util.HashMap<>();
    }
}
