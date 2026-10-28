package com.aiworkflow.designer.requirements.service;

import com.aiworkflow.designer.persistence.entity.WorkflowSpec;
import com.aiworkflow.designer.persistence.repository.WorkflowSpecRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequirementSpecService {

    private final WorkflowSpecRepository specRepository;

    public java.util.Optional<WorkflowSpec> findByConversationId(UUID conversationId) {
        return specRepository.findByConversationId(conversationId);
    }

    @Transactional
    public WorkflowSpec getOrCreate(UUID conversationId, UUID userId) {
        return specRepository.findByConversationId(conversationId)
                .orElseGet(() -> specRepository.save(WorkflowSpec.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .status("COLLECTING")
                        .build()));
    }

    @Transactional
    public WorkflowSpec save(WorkflowSpec spec) {
        return specRepository.save(spec);
    }

    @Transactional
    public WorkflowSpec markStatus(UUID conversationId, String status) {
        WorkflowSpec spec = specRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("No spec for conversation " + conversationId));
        spec.setStatus(status);
        return specRepository.save(spec);
    }

    @Transactional
    public WorkflowSpec markBuilt(UUID conversationId, UUID workflowId) {
        WorkflowSpec spec = specRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("No spec for conversation " + conversationId));
        spec.setStatus("BUILT");
        spec.setWorkflowId(workflowId);
        return specRepository.save(spec);
    }
}
