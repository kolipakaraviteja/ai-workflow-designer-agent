package com.aiworkflow.designer.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {

    private String id;
    private String agentType;
    private String currentStep;
    private String status; // IDLE, THINKING, EXECUTING, WAITING, COMPLETED, FAILED
    private Map<String, Object> context;
    private Map<String, Object> toolResults;
    private String lastToolCalled;
    private String lastUserMessage;
    private String lastAgentResponse;
    private LocalDateTime startedAt;
    private LocalDateTime lastActivityAt;
    private Integer stepsCompleted;
    private Integer totalSteps;

    public AgentState(String agentType) {
        this.id = UUID.randomUUID().toString();
        this.agentType = agentType;
        this.status = "IDLE";
        this.context = new java.util.HashMap<>();
        this.toolResults = new java.util.HashMap<>();
        this.startedAt = LocalDateTime.now();
        this.lastActivityAt = LocalDateTime.now();
        this.stepsCompleted = 0;
        this.totalSteps = 0;
    }

    public void updateStatus(String status) {
        this.status = status;
        this.lastActivityAt = LocalDateTime.now();
    }

    public void addContext(String key, Object value) {
        if (this.context == null) {
            this.context = new java.util.HashMap<>();
        }
        this.context.put(key, value);
    }

    public Object getContext(String key) {
        return context != null ? context.get(key) : null;
    }

    public void addToolResult(String toolName, Object result) {
        if (this.toolResults == null) {
            this.toolResults = new java.util.HashMap<>();
        }
        this.toolResults.put(toolName, result);
        this.lastToolCalled = toolName;
    }

    public void incrementSteps() {
        this.stepsCompleted++;
        this.lastActivityAt = LocalDateTime.now();
    }

    public boolean isIdle() {
        return "IDLE".equals(status);
    }

    public boolean isThinking() {
        return "THINKING".equals(status);
    }

    public boolean isExecuting() {
        return "EXECUTING".equals(status);
    }

    public boolean isWaiting() {
        return "WAITING".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
