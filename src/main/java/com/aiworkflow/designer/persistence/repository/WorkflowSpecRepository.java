package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.WorkflowSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowSpecRepository extends JpaRepository<WorkflowSpec, UUID> {

    Optional<WorkflowSpec> findByConversationId(UUID conversationId);

    List<WorkflowSpec> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
