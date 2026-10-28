package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.bpmn.BpmnGenerator;
import com.aiworkflow.designer.bpmn.BpmnValidator;
import com.aiworkflow.designer.persistence.entity.Deployment;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.runtime.flowable.FlowableDeploymentService;
import com.aiworkflow.designer.runtime.flowable.FlowableProcessService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Deployment Management", description = "APIs for deploying and managing workflow runtime instances")
public class DeploymentController {

    private final WorkflowService workflowService;
    private final BpmnGenerator bpmnGenerator;
    private final BpmnValidator bpmnValidator;
    private final FlowableDeploymentService flowableDeploymentService;
    private final FlowableProcessService flowableProcessService;
    private final AccessControlService accessControl;

    @PostMapping("/workflow/{workflowId}")
    @Operation(summary = "Deploy a workflow", description = "Deploys a workflow to the Flowable BPMN engine for execution")
    public ResponseEntity<Map<String, Object>> deployWorkflow(
            @Parameter(description = "Workflow ID") @PathVariable UUID workflowId,
            @AuthenticationPrincipal Jwt jwt) {

        String deployedBy = accessControl.requireUsername(jwt);
        log.info("Deploying workflow: {} (user={})", workflowId, deployedBy);

        Workflow workflow = accessControl.requireWorkflowAccess(workflowId, jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());

        // Deploy only validated workflows
        BpmnValidator.ValidationResult validation = bpmnValidator.validate(definition);
        if (validation.getStatus() == BpmnValidator.ValidationStatus.INVALID) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                    "success", false,
                    "error", "Workflow failed validation",
                    "validationErrors", validation.getErrors()
            ));
        }

        // Generate BPMN XML and verify it parses/deploys cleanly before persisting
        String bpmnXml = bpmnGenerator.generateBpmnXml(definition);
        flowableDeploymentService.verifyDeployable(bpmnXml);

        // Deploy to Flowable
        String deploymentId = flowableDeploymentService.deployProcessDefinition(
                workflow.getName(),
                bpmnXml
        );

        // Extract process definition key
        String processDefinitionKey = flowableDeploymentService.extractProcessDefinitionKey(deploymentId);

        // Create deployment record
        Deployment deployment = workflowService.createDeployment(
                workflowId,
                workflow.getVersion(),
                deploymentId,
                processDefinitionKey,
                "DEPLOYED",
                deployedBy,
                "production"
        );

        log.info("Workflow deployed successfully. Deployment ID: {}", deploymentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "deploymentId", deploymentId,
                "deployment", deployment,
                "workflowName", workflow.getName()
        ));
    }

    @GetMapping("/workflow/{workflowId}")
    @Operation(summary = "Get workflow deployments", description = "Retrieves all deployments for a specific workflow")
    public ResponseEntity<List<Deployment>> getWorkflowDeployments(
            @Parameter(description = "Workflow ID") @PathVariable UUID workflowId,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting deployments for workflow: {}", workflowId);

        accessControl.requireWorkflowAccess(workflowId, jwt);
        List<Deployment> deployments = workflowService.getDeploymentsByWorkflow(workflowId);
        return ResponseEntity.ok(deployments);
    }

    @GetMapping("/{deploymentId}")
    public ResponseEntity<Deployment> getDeployment(@PathVariable UUID deploymentId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting deployment: {}", deploymentId);

        Deployment deployment = accessControl.requireDeploymentAccess(deploymentId, jwt);
        return ResponseEntity.ok(deployment);
    }

    @GetMapping("/{deploymentId}/status")
    public ResponseEntity<Map<String, Object>> getDeploymentStatus(@PathVariable UUID deploymentId,
                                                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting deployment status: {}", deploymentId);

        Deployment deployment = accessControl.requireDeploymentAccess(deploymentId, jwt);

        var processStatus = flowableProcessService.getProcessDefinitionStatus(deployment.getEngineProcessDefinitionId());

        return ResponseEntity.ok(Map.of(
                "deployment", deployment,
                "processStatus", processStatus
        ));
    }

    @PostMapping("/{deploymentId}/instances")
    @Operation(summary = "Start a process instance", description = "Starts a new process instance from a deployed workflow")
    public ResponseEntity<Map<String, Object>> startProcessInstance(
            @Parameter(description = "Deployment ID") @PathVariable UUID deploymentId,
            @RequestBody(required = false) Map<String, Object> variables,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Starting process instance for deployment: {}", deploymentId);

        Deployment deployment = accessControl.requireDeploymentAccess(deploymentId, jwt);

        // Start the engine instance and persist the application record in one transaction
        var processInstance = flowableProcessService.startProcessInstanceForDeployment(
                deployment,
                deployment.getWorkflow(),
                variables
        );

        String processInstanceId = processInstance.getEngineProcessInstanceId();

        log.info("Process instance started: {}", processInstanceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "processInstanceId", processInstanceId,
                "processInstance", processInstance
        ));
    }

    @GetMapping("/{deploymentId}/instances")
    public ResponseEntity<List<Map<String, Object>>> getProcessInstances(@PathVariable UUID deploymentId,
                                                                         @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting process instances for deployment: {}", deploymentId);

        Deployment deployment = accessControl.requireDeploymentAccess(deploymentId, jwt);

        List<Map<String, Object>> instances = flowableProcessService.getProcessInstances(
                deployment.getEngineProcessDefinitionId()
        );

        return ResponseEntity.ok(instances);
    }

    @GetMapping("/instances/{processInstanceId}")
    public ResponseEntity<Map<String, Object>> getProcessInstance(@PathVariable String processInstanceId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        log.info("Getting process instance: {}", processInstanceId);

        var processInstance = flowableProcessService.getProcessInstance(processInstanceId);

        // The caller must own the workflow this instance belongs to
        if (processInstance.getWorkflow() != null) {
            accessControl.requireWorkflowAccess(processInstance.getWorkflow().getId(), jwt);
        } else if (!accessControl.isAdmin(jwt)) {
            accessControl.requireUsername(jwt);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "processInstance", processInstance
        ));
    }

    @DeleteMapping("/{deploymentId}")
    public ResponseEntity<Void> undeployWorkflow(@PathVariable UUID deploymentId,
                                                 @RequestParam(defaultValue = "false") boolean force,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Undeploying workflow deployment: {} (force={})", deploymentId, force);

        Deployment deployment = accessControl.requireDeploymentAccess(deploymentId, jwt);

        long activeInstances = flowableProcessService
                .countActiveInstances(deployment.getEngineProcessDefinitionId());
        if (activeInstances > 0 && !force) {
            throw new IllegalStateException("Deployment has " + activeInstances
                    + " active process instance(s). Re-run with force=true to undeploy anyway.");
        }

        // Undeploy from Flowable — cascade only when explicitly forced
        flowableDeploymentService.undeployProcessDefinition(deployment.getDeploymentId(), force);

        // Update deployment status
        workflowService.updateDeploymentStatus(deploymentId, "UNDEPLOYED");

        log.info("Workflow undeployed successfully");
        return ResponseEntity.noContent().build();
    }
}
