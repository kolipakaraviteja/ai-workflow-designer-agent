package com.aiworkflow.designer.runtime.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {

    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String status; // COMPLETED, FAILED, SKIPPED
    private long timestamp;
    
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    public ExecutionStep(String nodeId, String nodeName, String nodeType, String status, long timestamp) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.status = status;
        this.timestamp = timestamp;
        this.variables = new HashMap<>();
    }
}
