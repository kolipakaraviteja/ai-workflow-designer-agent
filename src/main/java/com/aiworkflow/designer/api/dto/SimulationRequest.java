package com.aiworkflow.designer.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {

    private UUID workflowId;
    private Map<String, Object> inputVariables;
    private String changeDescription; // For what-if analysis
    /** Optional alternative variables for what-if comparison runs. */
    private Map<String, Object> modifiedVariables;
}
