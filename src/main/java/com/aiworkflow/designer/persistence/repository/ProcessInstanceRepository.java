package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, UUID> {

    List<ProcessInstance> findByWorkflow_Id(UUID workflowId);

    List<ProcessInstance> findByDeployment_Id(UUID deploymentId);

    List<ProcessInstance> findByStatus(String status);

    ProcessInstance findByEngineProcessInstanceId(String engineProcessInstanceId);
}
