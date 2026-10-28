package com.aiworkflow.designer.conversation.service;

import com.aiworkflow.designer.persistence.entity.Conversation;
import com.aiworkflow.designer.persistence.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public UUID createConversationId() {
        return UUID.randomUUID();
    }

    @Transactional
    public Conversation recordMessage(UUID conversationId, UUID workflowId, UUID userId,
                                      String role, String content) {
        Conversation message = Conversation.builder()
                .conversationId(conversationId)
                .workflowId(workflowId)
                .userId(userId)
                .role(role)
                .content(content)
                .build();
        return conversationRepository.save(message);
    }

    public List<Conversation> getHistory(UUID conversationId) {
        return conversationRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public List<Conversation> getWorkflowHistory(UUID workflowId) {
        return conversationRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }

    public List<UUID> listConversationIds(UUID userId) {
        return conversationRepository.findDistinctConversationIdsByUserId(userId);
    }

    /**
     * Most recent conversation for a workflow/user pair — used to restore the
     * active conversation when a workflow is reopened.
     */
    public java.util.Optional<UUID> findLatestConversationId(UUID workflowId, UUID userId) {
        List<UUID> ids = conversationRepository.findLatestConversationIds(
                workflowId, userId, org.springframework.data.domain.PageRequest.of(0, 1));
        return ids.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(ids.get(0));
    }

    @Transactional
    public void deleteConversation(UUID conversationId) {
        conversationRepository.deleteByConversationId(conversationId);
    }
}
