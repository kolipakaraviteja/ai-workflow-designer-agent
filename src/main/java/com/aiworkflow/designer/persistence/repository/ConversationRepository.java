package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByWorkflowIdOrderByCreatedAtAsc(UUID workflowId);

    List<Conversation> findByWorkflowIdAndRoleOrderByCreatedAtAsc(UUID workflowId, String role);

    List<Conversation> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    void deleteByConversationId(UUID conversationId);

    @Query("SELECT DISTINCT c.conversationId FROM Conversation c WHERE c.userId = :userId ORDER BY c.conversationId")
    List<UUID> findDistinctConversationIdsByUserId(UUID userId);

    @Query("SELECT c.conversationId FROM Conversation c WHERE c.workflowId = :workflowId "
            + "AND (c.userId = :userId OR c.userId IS NULL) "
            + "GROUP BY c.conversationId ORDER BY MAX(c.createdAt) DESC")
    List<UUID> findLatestConversationIds(@Param("workflowId") UUID workflowId,
                                         @Param("userId") UUID userId,
                                         Pageable pageable);
}
