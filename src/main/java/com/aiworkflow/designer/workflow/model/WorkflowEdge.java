package com.aiworkflow.designer.workflow.model;

import java.util.Map;

public class WorkflowEdge {

    private String id;
    private String source;
    private String target;
    private String condition;
    private String type;
    private Map<String, Object> properties;
    private Map<String, Object> metadata;

    public WorkflowEdge() {
        this.properties = new java.util.HashMap<>();
        this.metadata = new java.util.HashMap<>();
    }

    public WorkflowEdge(String source, String target) {
        this();
        this.id = java.util.UUID.randomUUID().toString();
        this.source = source;
        this.target = target;
        this.type = "SEQUENCE_FLOW";
    }

    public WorkflowEdge(String source, String target, String condition) {
        this(source, target);
        this.condition = condition;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public boolean hasCondition() {
        return condition != null && !condition.trim().isEmpty();
    }

    public boolean isDefaultFlow() {
        return "DEFAULT_FLOW".equals(type);
    }

    public boolean isMessageFlow() {
        return "MESSAGE_FLOW".equals(type);
    }
}
