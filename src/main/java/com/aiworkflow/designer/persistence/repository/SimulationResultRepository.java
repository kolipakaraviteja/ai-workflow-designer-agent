package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.SimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SimulationResultRepository extends JpaRepository<SimulationResult, UUID> {

    List<SimulationResult> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);
}
