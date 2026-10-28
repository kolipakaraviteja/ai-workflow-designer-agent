package com.aiworkflow.designer.runtime.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

    private String workflowName;
    
    @Builder.Default
    private Map<String, Object> inputVariables = new HashMap<>();
    
    @Builder.Default
    private Map<String, Object> outputVariables = new HashMap<>();
    
    @Builder.Default
    private List<String> executionPath = new ArrayList<>();
    
    @Builder.Default
    private List<ExecutionStep> executionDetails = new ArrayList<>();
    
    @Builder.Default
    private Map<String, GatewayDecision> gatewayDecisions = new HashMap<>();

    /** Honesty annotations: unevaluated conditions, step-cap hits, etc. */
    @Builder.Default
    private List<String> notes = new ArrayList<>();

    @Builder.Default
    private boolean success = true;
    
    private String errorMessage;
    
    @Builder.Default
    private long startTime = System.currentTimeMillis();
    
    @Builder.Default
    private long endTime = System.currentTimeMillis();
    
    @Builder.Default
    private long duration = 0;
}
