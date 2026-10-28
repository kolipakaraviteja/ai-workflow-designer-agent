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
public class AgentRequest {

    private UUID workflowId;
    private UUID conversationId;
    private String message;
    private String agentType; // ORCHESTRATOR, DESIGNER, VALIDATOR, OPTIMIZER, RUNTIME
    private Map<String, Object> context;
}
