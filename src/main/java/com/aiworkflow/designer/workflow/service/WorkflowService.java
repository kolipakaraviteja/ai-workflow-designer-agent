package com.aiworkflow.designer.workflow.service;

import com.aiworkflow.designer.bpmn.BpmnGenerator;
import com.aiworkflow.designer.bpmn.BpmnValidator;
import com.aiworkflow.designer.persistence.entity.Deployment;
import com.aiworkflow.designer.persistence.entity.ProcessInstance;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.persistence.entity.WorkflowVersion;
import com.aiworkflow.designer.persistence.repository.DeploymentRepository;
import com.aiworkflow.designer.persistence.repository.ProcessInstanceRepository;
import com.aiworkflow.designer.persistence.repository.WorkflowRepository;
import com.aiworkflow.designer.persistence.repository.WorkflowVersionRepository;
import com.aiworkflow.designer.requirements.service.RequirementSpecService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final DeploymentRepository deploymentRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final BpmnGenerator bpmnGenerator;
    private final BpmnValidator bpmnValidator;
    private final RequirementSpecService specService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Workflow createWorkflow(WorkflowDefinition definition, String createdBy) {
        requireIntegrity(definition);
        Workflow workflow = Workflow.builder()
                .name(definition.getName())
                .description(definition.getDescription())
                .version(1)
                .definitionJson(serializeDefinition(definition))
                .status("DRAFT")
                .createdBy(createdBy)
                .build();
        workflow = workflowRepository.save(workflow);
        snapshotVersion(workflow, definition, createdBy, "Initial version");
        return workflow;
    }

    @Transactional
    public Workflow updateWorkflow(UUID workflowId, WorkflowDefinition definition, String updatedBy) {
        requireIntegrity(definition);
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        workflow.setName(definition.getName());
        workflow.setDescription(definition.getDescription());
        workflow.setDefinitionJson(serializeDefinition(definition));
        workflow.setUpdatedBy(updatedBy);
        workflow.setVersion(nextVersion(workflow, definition));

        workflow = workflowRepository.save(workflow);
        snapshotVersion(workflow, definition, updatedBy, "Updated via API");
        return workflow;
    }

    @Transactional
    public Workflow updateBpmnXml(UUID workflowId, String bpmnXml) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        workflow.setBpmnXml(bpmnXml);
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow updateStatus(UUID workflowId, String status) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        workflow.setStatus(status);
        return workflowRepository.save(workflow);
    }

    /**
     * Atomically persists an agent-produced workflow definition, its BPMN XML, and
     * (when the spec was confirmed AND the workflow validates) marks the requirement
     * spec as BUILT. BPMN is generated before any database write, so a generation or
     * persistence failure cannot leave definitionJson and bpmnXml out of sync.
     *
     * Structural integrity violations (duplicate ids, dangling refs) are hard
     * failures — corrupted graphs never reach the database. Semantic validation
     * errors (unreachable nodes, dead ends, missing assignees/implementations) do
     * NOT block persistence: the draft is saved with status INVALID so the user can
     * keep iterating in the conversation, and the issues are returned to the caller
     * for surfacing. The strict gate for semantic validity is deployment
     * ({@link com.aiworkflow.designer.api.controller.DeploymentController} returns
     * 422 for INVALID workflows).
     */
    @Transactional
    public AgentPersistResult persistAgentResult(UUID workflowId, WorkflowDefinition definition,
                                                 String actor, UUID conversationId, boolean specConfirmed) {
        requireIntegrity(definition);
        BpmnValidator.ValidationResult validation = bpmnValidator.validate(definition);
        String bpmnXml = bpmnGenerator.generateBpmnXml(definition);

        Workflow workflow;
        if (workflowId != null) {
            workflow = workflowRepository.findById(workflowId)
                    .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));
            workflow.setName(definition.getName());
            workflow.setDescription(definition.getDescription());
            workflow.setUpdatedBy(actor);
            workflow.setVersion(nextVersion(workflow, definition));
        } else {
            workflow = Workflow.builder()
                    .name(definition.getName())
                    .description(definition.getDescription())
                    .version(1)
                    .createdBy(actor)
                    .build();
        }
        workflow.setStatus(validation.hasErrors() ? "INVALID" : "DRAFT");
        definition.setVersion(workflow.getVersion());
        workflow.setDefinitionJson(serializeDefinition(definition));
        workflow.setBpmnXml(bpmnXml);
        workflow = workflowRepository.save(workflow);

        snapshotVersion(workflow, definition, actor, "Agent-designed update");

        if (specConfirmed && !validation.hasErrors() && conversationId != null) {
            specService.markBuilt(conversationId, workflow.getId());
        }
        return new AgentPersistResult(workflow, validation);
    }

    public record AgentPersistResult(Workflow workflow, BpmnValidator.ValidationResult validation) {}

    private void requireIntegrity(WorkflowDefinition definition) {
        List<String> violations = com.aiworkflow.designer.tools.GraphInvariants.check(definition);
        if (!violations.isEmpty()) {
            throw new WorkflowValidationException(violations);
        }
    }

    private int nextVersion(Workflow entity, WorkflowDefinition definition) {
        int current = entity.getVersion() != null ? entity.getVersion()
                : (definition.getVersion() != null ? definition.getVersion() : 0);
        return current + 1;
    }

    private void snapshotVersion(Workflow workflow, WorkflowDefinition definition,
                                 String actor, String changeDescription) {
        workflowVersionRepository.save(WorkflowVersion.builder()
                .workflowId(workflow.getId())
                .version(workflow.getVersion())
                .definitionJson(workflow.getDefinitionJson())
                .bpmnXml(workflow.getBpmnXml())
                .changeDescription(changeDescription)
                .createdBy(actor)
                .build());
    }

    public Optional<Workflow> getWorkflow(UUID workflowId) {
        return workflowRepository.findById(workflowId);
    }

    public Optional<Workflow> getWorkflowByName(String name) {
        return workflowRepository.findByName(name);
    }

    public List<Workflow> getAllWorkflows() {
        return workflowRepository.findAll();
    }

    public List<Workflow> getWorkflowsByStatus(String status) {
        return workflowRepository.findByStatus(status);
    }

    public List<Workflow> getWorkflowsByUser(String username) {
        return workflowRepository.findByCreatedByOrderByUpdatedAtDesc(username);
    }

    public List<Workflow> getWorkflowsByUserAndStatus(String username, String status) {
        return workflowRepository.findByCreatedByAndStatusOrderByUpdatedAtDesc(username, status);
    }

    public List<Workflow> searchWorkflows(String keyword) {
        return workflowRepository.searchByKeyword(keyword);
    }

    @Transactional
    public void deleteWorkflow(UUID workflowId) {
        workflowRepository.deleteById(workflowId);
    }

    public WorkflowDefinition deserializeDefinition(String json) {
        try {
            return objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (Exception e) {
            log.error("Failed to deserialize workflow definition", e);
            throw new RuntimeException("Failed to deserialize workflow definition", e);
        }
    }

    public String serializeDefinition(WorkflowDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (Exception e) {
            log.error("Failed to serialize workflow definition", e);
            throw new RuntimeException("Failed to serialize workflow definition", e);
        }
    }

    @Transactional
    public Deployment createDeployment(UUID workflowId, Integer version, String deploymentId,
                                       String processDefinitionKey, String status, String deployedBy, String environment) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        Deployment deployment = new Deployment();
        deployment.setWorkflow(workflow);
        deployment.setWorkflowVersion(version);
        deployment.setDeploymentId(deploymentId);
        deployment.setEngineProcessDefinitionId(processDefinitionKey);
        deployment.setStatus(status);
        deployment.setDeployedBy(deployedBy);

        return deploymentRepository.save(deployment);
    }

    public Optional<Deployment> getDeployment(UUID deploymentId) {
        return deploymentRepository.findById(deploymentId);
    }

    public List<Deployment> getDeploymentsByWorkflow(UUID workflowId) {
        return deploymentRepository.findByWorkflow_IdOrderByDeployedAtDesc(workflowId);
    }

    @Transactional
    public Deployment updateDeploymentStatus(UUID deploymentId, String status) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

        deployment.setStatus(status);
        return deploymentRepository.save(deployment);
    }

    @Transactional
    public ProcessInstance createProcessInstance(UUID workflowId, UUID deploymentId, String processInstanceId,
                                                String processDefinitionKey, String status, Map<String, Object> variables) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setWorkflow(workflow);
        processInstance.setDeployment(deployment);
        processInstance.setEngineProcessInstanceId(processInstanceId);
        processInstance.setStatus(status);
        processInstance.setVariables(variables);

        return processInstanceRepository.save(processInstance);
    }

    public Optional<ProcessInstance> getProcessInstance(UUID processInstanceId) {
        return processInstanceRepository.findById(processInstanceId);
    }

    public List<ProcessInstance> getProcessInstancesByWorkflow(UUID workflowId) {
        return processInstanceRepository.findByWorkflow_Id(workflowId);
    }

    public List<ProcessInstance> getProcessInstancesByDeployment(UUID deploymentId) {
        return processInstanceRepository.findByDeployment_Id(deploymentId);
    }

    @Transactional
    public ProcessInstance updateProcessInstanceStatus(UUID processInstanceId, String status) {
        ProcessInstance processInstance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new RuntimeException("Process instance not found: " + processInstanceId));

        processInstance.setStatus(status);
        return processInstanceRepository.save(processInstance);
    }
}
