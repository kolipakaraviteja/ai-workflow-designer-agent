package com.aiworkflow.designer.workflow.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowDefinition {

    private String id;
    private String name;
    private String description;
    
    @Builder.Default
    private Integer version = 1;
    
    @Builder.Default
    private List<WorkflowNode> nodes = new ArrayList<>();
    
    @Builder.Default
    private List<WorkflowEdge> edges = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> variables = new java.util.HashMap<>();
    
    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();

    public WorkflowDefinition(String name, String description) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.version = 1;
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.variables = new java.util.HashMap<>();
        this.metadata = new java.util.HashMap<>();
    }

    public void addNode(WorkflowNode node) {
        if (this.nodes == null) {
            this.nodes = new ArrayList<>();
        }
        this.nodes.add(node);
    }

    public void addEdge(WorkflowEdge edge) {
        if (this.edges == null) {
            this.edges = new ArrayList<>();
        }
        this.edges.add(edge);
    }

    @JsonIgnore
    public WorkflowNode getNodeById(String nodeId) {
        return nodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    @JsonIgnore
    public List<WorkflowNode> getNodesByType(String type) {
        return nodes.stream()
                .filter(node -> node.getType().equals(type))
                .toList();
    }

    @JsonIgnore
    public WorkflowNode getStartEvent() {
        return nodes.stream()
                .filter(node -> node.getType().equals("START_EVENT"))
                .findFirst()
                .orElse(null);
    }

    @JsonIgnore
    public List<WorkflowNode> getEndEvents() {
        return nodes.stream()
                .filter(node -> node.getType().equals("END_EVENT"))
                .toList();
    }

    @JsonIgnore
    public List<WorkflowEdge> getEdgesFromNode(String nodeId) {
        return edges.stream()
                .filter(edge -> edge.getSource().equals(nodeId))
                .toList();
    }

    @JsonIgnore
    public List<WorkflowEdge> getEdgesToNode(String nodeId) {
        return edges.stream()
                .filter(edge -> edge.getTarget().equals(nodeId))
                .toList();
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert workflow to JSON", e);
        }
    }
}
