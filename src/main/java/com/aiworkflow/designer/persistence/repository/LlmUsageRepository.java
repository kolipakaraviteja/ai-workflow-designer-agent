package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.LlmUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LlmUsageRepository extends JpaRepository<LlmUsage, UUID> {

    List<LlmUsage> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<LlmUsage> findByUsernameOrderByCreatedAtDesc(String username);

    List<LlmUsage> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);
}
