package com.aiworkflow.designer.api.dto;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {

    private UUID id;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private WorkflowDefinition definition;
    private String bpmnXml;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
