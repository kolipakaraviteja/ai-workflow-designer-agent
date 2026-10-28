package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.AgentExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

    List<AgentExecution> findByWorkflowIdOrderByStartedAtDesc(UUID workflowId);

    List<AgentExecution> findByAgentTypeOrderByStartedAtDesc(String agentType);
}
