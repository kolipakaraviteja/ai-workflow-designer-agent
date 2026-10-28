package com.aiworkflow.designer.api.dto;

import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String agentType;
    private String status;
    private String message;
    private java.util.UUID conversationId;
    private AgentState agentState;
    private WorkflowDefinition modifiedWorkflow;
    private java.util.UUID workflowId;
    private String bpmnXml;
    private Object workflowSpec;
    private String correlationId;
    private String agentPath;
    private Integer toolCallCount;
    private java.util.Map<String, Object> tokenUsage;
    private String validationStatus;
    private java.util.List<String> validationErrors;
    private java.util.List<String> validationWarnings;
    private String error;
}
