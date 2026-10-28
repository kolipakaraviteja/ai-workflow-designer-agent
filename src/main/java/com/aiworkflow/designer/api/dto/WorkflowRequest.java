package com.aiworkflow.designer.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {

    private String name;
    private String description;
    private String createdBy;
    private String updatedBy;
}
