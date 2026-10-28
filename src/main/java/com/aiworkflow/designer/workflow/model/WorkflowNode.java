package com.aiworkflow.designer.workflow.model;

import java.util.Map;

public class WorkflowNode {

    private String id;
    private String type;
    private String name;
    private String description;
    private Map<String, Object> properties;
    private Map<String, Object> metadata;

    public WorkflowNode() {
        this.properties = new java.util.HashMap<>();
        this.metadata = new java.util.HashMap<>();
    }

    public WorkflowNode(String id, String type, String name) {
        this();
        this.id = id;
        this.type = type;
        this.name = name;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Object getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }

    public void setProperty(String key, Object value) {
        if (this.properties == null) {
            this.properties = new java.util.HashMap<>();
        }
        this.properties.put(key, value);
    }

    public boolean isEvent() {
        return type != null && (type.endsWith("_EVENT") || type.equals("INTERMEDIATE_EVENT"));
    }

    public boolean isTask() {
        return type != null && type.endsWith("_TASK");
    }

    public boolean isGateway() {
        return type != null && type.endsWith("_GATEWAY");
    }

    public boolean isStartEvent() {
        return "START_EVENT".equals(type);
    }

    public boolean isEndEvent() {
        return "END_EVENT".equals(type);
    }
}
