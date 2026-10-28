package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.api.dto.WorkflowRequest;
import com.aiworkflow.designer.api.dto.WorkflowResponse;
import com.aiworkflow.designer.bpmn.BpmnGenerator;
import com.aiworkflow.designer.bpmn.BpmnParser;
import com.aiworkflow.designer.bpmn.BpmnValidator;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.security.AccessControlService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workflow Management", description = "APIs for managing BPMN workflows")
public class WorkflowController {

    private static final long MAX_BPMN_IMPORT_BYTES = 2 * 1024 * 1024;

    private final WorkflowService workflowService;
    private final BpmnGenerator bpmnGenerator;
    private final BpmnParser bpmnParser;
    private final BpmnValidator bpmnValidator;
    private final AccessControlService accessControl;

    @PostMapping
    @Operation(summary = "Create a new workflow", description = "Creates a new BPMN workflow with the given name and description")
    public ResponseEntity<WorkflowResponse> createWorkflow(@RequestBody WorkflowRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        String createdBy = accessControl.requireUsername(jwt);
        log.info("Creating workflow: {} (user={})", request.getName(), createdBy);

        WorkflowDefinition definition = new WorkflowDefinition(request.getName(), request.getDescription());
        Workflow workflow = workflowService.createWorkflow(definition, createdBy);

        // Generate BPMN XML
        String bpmnXml = bpmnGenerator.generateBpmnXml(definition);
        workflowService.updateBpmnXml(workflow.getId(), bpmnXml);

        WorkflowResponse response = toResponse(workflow, definition);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a workflow by ID", description = "Retrieves a specific workflow by its unique identifier")
    public ResponseEntity<WorkflowResponse> getWorkflow(@Parameter(description = "Workflow ID") @PathVariable UUID id,
                                                        @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting workflow: {}", id);

        Workflow workflow = accessControl.requireWorkflowAccess(id, jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
        WorkflowResponse response = toResponse(workflow, definition);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all workflows", description = "Retrieves all workflows with optional filtering by status or username")
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows(
            @Parameter(description = "Filter by workflow status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by creator username") @RequestParam(required = false) String username,
            @AuthenticationPrincipal Jwt jwt) {

        // Non-admin users only see their own workflows; admin may filter by any user
        String effectiveUser = accessControl.isAdmin(jwt)
                ? username
                : accessControl.requireUsername(jwt);

        List<Workflow> workflows;
        if (effectiveUser != null && !effectiveUser.isEmpty()) {
            workflows = (status != null && !status.isEmpty())
                    ? workflowService.getWorkflowsByUserAndStatus(effectiveUser, status)
                    : workflowService.getWorkflowsByUser(effectiveUser);
        } else if (status != null && !status.isEmpty()) {
            workflows = workflowService.getWorkflowsByStatus(status);
        } else {
            workflows = workflowService.getAllWorkflows();
        }

        List<WorkflowResponse> responses = workflows.stream()
                .map(workflow -> {
                    WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
                    return toResponse(workflow, definition);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
            @PathVariable UUID id,
            @RequestBody WorkflowRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Updating workflow: {}", id);

        Workflow workflow = accessControl.requireWorkflowAccess(id, jwt);
        String updatedBy = accessControl.requireUsername(jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());

        workflow = workflowService.updateWorkflow(id, definition, updatedBy);

        // Regenerate BPMN XML
        String bpmnXml = bpmnGenerator.generateBpmnXml(definition);
        workflowService.updateBpmnXml(workflow.getId(), bpmnXml);

        WorkflowResponse response = toResponse(workflow, definition);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable UUID id,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Deleting workflow: {}", id);

        accessControl.requireWorkflowAccess(id, jwt);
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate a workflow", description = "Validates the BPMN structure and logic of a workflow")
    public ResponseEntity<Object> validateWorkflow(@Parameter(description = "Workflow ID") @PathVariable UUID id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Validating workflow: {}", id);

        Workflow workflow = accessControl.requireWorkflowAccess(id, jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
        BpmnValidator.ValidationResult result = bpmnValidator.validate(definition);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/generate-bpmn")
    public ResponseEntity<String> generateBpmn(@PathVariable UUID id,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Generating BPMN for workflow: {}", id);

        Workflow workflow = accessControl.requireWorkflowAccess(id, jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
        String bpmnXml = bpmnGenerator.generateBpmnXml(definition);

        // Save BPMN XML
        workflowService.updateBpmnXml(workflow.getId(), bpmnXml);

        return ResponseEntity.ok(bpmnXml);
    }

    @PostMapping("/import-bpmn")
    public ResponseEntity<WorkflowResponse> importBpmn(
            @RequestBody String bpmnXml,
            @AuthenticationPrincipal Jwt jwt) {

        String createdBy = accessControl.requireUsername(jwt);
        if (bpmnXml == null || bpmnXml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BPMN_IMPORT_BYTES) {
            throw new IllegalArgumentException("BPMN document exceeds the maximum import size of "
                    + (MAX_BPMN_IMPORT_BYTES / 1024 / 1024) + " MB");
        }

        log.info("Importing BPMN XML ({} bytes, user={})", bpmnXml.length(), createdBy);

        WorkflowDefinition definition = bpmnParser.parseBpmnXml(bpmnXml);
        Workflow workflow = workflowService.createWorkflow(definition, createdBy);

        // Save the original BPMN XML
        workflowService.updateBpmnXml(workflow.getId(), bpmnXml);

        WorkflowResponse response = toResponse(workflow, definition);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/bpmn")
    public ResponseEntity<String> getBpmnXml(@PathVariable UUID id,
                                             @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting BPMN XML for workflow: {}", id);

        Workflow workflow = accessControl.requireWorkflowAccess(id, jwt);

        if (workflow.getBpmnXml() == null) {
            // Generate BPMN if not exists
            WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
            String bpmnXml = bpmnGenerator.generateBpmnXml(definition);
            workflowService.updateBpmnXml(workflow.getId(), bpmnXml);
            return ResponseEntity.ok(bpmnXml);
        }

        return ResponseEntity.ok(workflow.getBpmnXml());
    }

    private WorkflowResponse toResponse(Workflow workflow, WorkflowDefinition definition) {
        return WorkflowResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .version(workflow.getVersion())
                .status(workflow.getStatus())
                .definition(definition)
                .bpmnXml(workflow.getBpmnXml())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .createdBy(workflow.getCreatedBy())
                .build();
    }
}
