package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    List<WorkflowVersion> findByWorkflowIdOrderByVersionDesc(UUID workflowId);

    WorkflowVersion findByWorkflowIdAndVersion(UUID workflowId, Integer version);
}
