package com.aiworkflow.designer.runtime.flowable;

import com.aiworkflow.designer.persistence.entity.ProcessInstance;
import com.aiworkflow.designer.persistence.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowableProcessService {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final ProcessInstanceRepository processInstanceRepository;

    @Transactional
    public com.aiworkflow.designer.persistence.entity.ProcessInstance startProcess(String processDefinitionKey, Map<String, Object> variables, String startedBy) {
        log.info("Starting process instance for: {}", processDefinitionKey);

        try {
            // Start process instance in Flowable
            org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService.startProcessInstanceByKey(
                    processDefinitionKey,
                    variables != null ? variables : new HashMap<>()
            );

            // Create process instance record
            com.aiworkflow.designer.persistence.entity.ProcessInstance instance = new com.aiworkflow.designer.persistence.entity.ProcessInstance();
            instance.setEngineProcessInstanceId(flowableInstance.getId());
            instance.setStatus("RUNNING");
            instance.setVariables(variables);
            instance.setCurrentActivity(Map.of(
                    "activityId", flowableInstance.getActivityId(),
                    "processDefinitionId", flowableInstance.getProcessDefinitionId()
            ));

            instance = processInstanceRepository.save(instance);

            log.info("Process instance started. ID: {}", flowableInstance.getId());
            return instance;

        } catch (Exception e) {
            log.error("Failed to start process instance", e);
            throw new RuntimeException("Failed to start process instance: " + e.getMessage(), e);
        }
    }

    public com.aiworkflow.designer.persistence.entity.ProcessInstance getProcessInstance(String processInstanceId) {
        log.info("Getting process instance: {}", processInstanceId);

        // Get or create our process instance record first — it is the
        // application-side source of truth for ownership and linkage.
        ProcessInstance existingInstance =
                processInstanceRepository.findByEngineProcessInstanceId(processInstanceId);
        if (existingInstance != null) {
            return existingInstance;
        }

        org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (flowableInstance != null) {
            com.aiworkflow.designer.persistence.entity.ProcessInstance newInstance = new com.aiworkflow.designer.persistence.entity.ProcessInstance();
            newInstance.setEngineProcessInstanceId(flowableInstance.getId());
            newInstance.setStatus(mapFlowableStatus(flowableInstance));
            newInstance.setVariables(runtimeService.getVariables(processInstanceId));
            newInstance.setCurrentActivity(Map.of(
                    "activityId", flowableInstance.getActivityId(),
                    "processDefinitionId", flowableInstance.getProcessDefinitionId()
            ));
            return processInstanceRepository.save(newInstance);
        }

        // Completed instances leave the runtime tables — check history before
        // reporting not-found.
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historic == null) {
            throw new IllegalArgumentException("Process instance not found: " + processInstanceId);
        }

        com.aiworkflow.designer.persistence.entity.ProcessInstance historicInstance = new com.aiworkflow.designer.persistence.entity.ProcessInstance();
        historicInstance.setEngineProcessInstanceId(historic.getId());
        historicInstance.setStatus(historic.getEndTime() != null ? "COMPLETED" : "RUNNING");
        if (historic.getEndTime() != null) {
            historicInstance.setCompletedAt(historic.getEndTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }
        return processInstanceRepository.save(historicInstance);
    }

    public List<com.aiworkflow.designer.persistence.entity.ProcessInstance> getActiveProcessInstances() {
        log.info("Getting all active process instances");

        List<org.flowable.engine.runtime.ProcessInstance> flowableInstances =
                runtimeService.createProcessInstanceQuery()
                        .active()
                        .list();

        List<com.aiworkflow.designer.persistence.entity.ProcessInstance> result = new ArrayList<>();
        for (org.flowable.engine.runtime.ProcessInstance fi : flowableInstances) {
            ProcessInstance existing =
                    processInstanceRepository.findByEngineProcessInstanceId(fi.getId());
            if (existing != null) {
                result.add(existing);
            } else {
                com.aiworkflow.designer.persistence.entity.ProcessInstance newInstance = new com.aiworkflow.designer.persistence.entity.ProcessInstance();
                newInstance.setEngineProcessInstanceId(fi.getId());
                newInstance.setStatus("RUNNING");
                newInstance.setVariables(runtimeService.getVariables(fi.getId()));
                newInstance.setCurrentActivity(Map.of(
                        "activityId", fi.getActivityId(),
                        "processDefinitionId", fi.getProcessDefinitionId()
                ));
                result.add(processInstanceRepository.save(newInstance));
            }
        }
        return result;
    }

    @Transactional
    public void completeTask(String taskId, Map<String, Object> variables) {
        log.info("Completing task: {}", taskId);

        try {
            // For now, just log that task completion would happen
            // In a full implementation, we would use TaskService
            log.info("Task completion not fully implemented in current version");

        } catch (Exception e) {
            log.error("Failed to complete task", e);
            throw new RuntimeException("Failed to complete task: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteProcessInstance(String processInstanceId, String deleteReason) {
        log.info("Deleting process instance: {}", processInstanceId);

        try {
            runtimeService.deleteProcessInstance(processInstanceId, deleteReason);

            // Update our record
            ProcessInstance instanceOpt = processInstanceRepository.findByEngineProcessInstanceId(processInstanceId);

            if (instanceOpt != null) {
                com.aiworkflow.designer.persistence.entity.ProcessInstance instance = instanceOpt;
                instance.setStatus("DELETED");
                instance.setCompletedAt(java.time.LocalDateTime.now());
                processInstanceRepository.save(instance);
            }

            log.info("Process instance deleted: {}", processInstanceId);

        } catch (Exception e) {
            log.error("Failed to delete process instance", e);
            throw new RuntimeException("Failed to delete process instance: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    public void setProcessVariable(String processInstanceId, String variableName, Object variableValue) {
        runtimeService.setVariable(processInstanceId, variableName, variableValue);
    }

    private String mapFlowableStatus(org.flowable.engine.runtime.ProcessInstance flowableInstance) {
        if (flowableInstance.isEnded()) {
            return "COMPLETED";
        } else if (flowableInstance.isSuspended()) {
            return "SUSPENDED";
        } else {
            return "RUNNING";
        }
    }

    @Transactional
    public String startProcessInstance(String processDefinitionKey, Map<String, Object> variables) {
        log.info("Starting process instance for process definition: {}", processDefinitionKey);

        try {
            org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService.startProcessInstanceByKey(
                    processDefinitionKey,
                    variables != null ? variables : new HashMap<>()
            );

            log.info("Process instance started. ID: {}", flowableInstance.getId());
            return flowableInstance.getId();

        } catch (Exception e) {
            log.error("Failed to start process instance", e);
            throw new RuntimeException("Failed to start process instance: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getProcessInstances(String processDefinitionKey) {
        log.info("Getting process instances for process definition: {}", processDefinitionKey);

        List<org.flowable.engine.runtime.ProcessInstance> flowableInstances =
                runtimeService.createProcessInstanceQuery()
                        .processDefinitionKey(processDefinitionKey)
                        .list();

        List<Map<String, Object>> result = new ArrayList<>(flowableInstances.stream()
                .map(fi -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("processInstanceId", fi.getId());
                    row.put("processDefinitionId", fi.getProcessDefinitionId());
                    row.put("processDefinitionKey", fi.getProcessDefinitionKey());
                    row.put("status", mapFlowableStatus(fi));
                    row.put("activityId", fi.getActivityId());
                    row.put("startTime", fi.getStartTime());
                    row.put("variables", runtimeService.getVariables(fi.getId()));
                    return row;
                })
                .toList());

        // Completed instances leave the runtime tables — merge history so the
        // list does not silently forget finished instances.
        Set<String> runtimeIds = flowableInstances.stream()
                .map(org.flowable.engine.runtime.ProcessInstance::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<HistoricProcessInstance> historic = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .finished()
                .list();
        for (HistoricProcessInstance hi : historic) {
            if (runtimeIds.contains(hi.getId())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("processInstanceId", hi.getId());
            row.put("processDefinitionId", hi.getProcessDefinitionId());
            row.put("processDefinitionKey", processDefinitionKey);
            row.put("status", "COMPLETED");
            row.put("activityId", null);
            row.put("startTime", hi.getStartTime());
            row.put("endTime", hi.getEndTime());
            result.add(row);
        }

        return result;
    }

    public Map<String, Object> getProcessDefinitionStatus(String processDefinitionKey) {
        log.info("Getting process definition status: {}", processDefinitionKey);

        org.flowable.engine.repository.ProcessDefinition processDefinition =
                repositoryService.createProcessDefinitionQuery()
                        .processDefinitionKey(processDefinitionKey)
                        .latestVersion()
                        .singleResult();

        if (processDefinition == null) {
            throw new IllegalArgumentException("Process definition not found: " + processDefinitionKey);
        }

        long activeInstanceCount = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .active()
                .count();

        return Map.of(
                "processDefinitionId", processDefinition.getId(),
                "processDefinitionKey", processDefinitionKey,
                "version", processDefinition.getVersion(),
                "deploymentId", processDefinition.getDeploymentId(),
                "name", processDefinition.getName(),
                "suspended", processDefinition.isSuspended(),
                "activeInstanceCount", activeInstanceCount
        );
    }

    public long countActiveInstances(String processDefinitionKey) {
        return runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .active()
                .count();
    }

    /**
     * Starts a Flowable instance and persists the application-side record in
     * one transaction so a database failure cannot leave an engine instance
     * without its application record.
     */
    @Transactional
    public ProcessInstance startProcessInstanceForDeployment(
            com.aiworkflow.designer.persistence.entity.Deployment deployment,
            com.aiworkflow.designer.persistence.entity.Workflow workflow,
            Map<String, Object> variables) {

        org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService.startProcessInstanceByKey(
                deployment.getEngineProcessDefinitionId(),
                variables != null ? variables : new HashMap<>()
        );

        ProcessInstance instance = new ProcessInstance();
        instance.setWorkflow(workflow);
        instance.setDeployment(deployment);
        instance.setEngineProcessInstanceId(flowableInstance.getId());
        instance.setStatus("RUNNING");
        instance.setVariables(variables);
        instance.setCurrentActivity(Map.of(
                "activityId", flowableInstance.getActivityId(),
                "processDefinitionId", flowableInstance.getProcessDefinitionId()
        ));

        return processInstanceRepository.save(instance);
    }
}
