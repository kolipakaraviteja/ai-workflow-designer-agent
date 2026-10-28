package com.aiworkflow.designer.api.dto;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    private String filename;
    private String purpose;
    private int extractedCharacters;
    private Integer chunkCount;
    private UUID documentSourceId;
    private WorkflowDefinition workflow;
    private String message;
}
