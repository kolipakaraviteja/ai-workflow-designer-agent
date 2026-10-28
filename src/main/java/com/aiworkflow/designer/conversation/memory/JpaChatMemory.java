package com.aiworkflow.designer.conversation.memory;

import com.aiworkflow.designer.persistence.entity.Conversation;
import com.aiworkflow.designer.persistence.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaChatMemory implements ChatMemory {

    private final ConversationRepository conversationRepository;

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        // Internal agent calls run under the default id - never persist them.
        if (AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_CONVERSATION_ID.equals(conversationId)) {
            return;
        }
        UUID convId = UUID.fromString(conversationId);
        for (Message message : messages) {
            Conversation row = new Conversation();
            row.setConversationId(convId);
            row.setWorkflowId(ConversationContext.getWorkflowId());
            row.setUserId(ConversationContext.getUserId());
            row.setRole(message.getMessageType().name());
            row.setContent(message.getContent());
            conversationRepository.save(row);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_CONVERSATION_ID.equals(conversationId)) {
            return List.of();
        }
        UUID convId;
        try {
            convId = UUID.fromString(conversationId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }

        List<Conversation> rows = conversationRepository.findByConversationIdOrderByCreatedAtAsc(convId);
        int from = Math.max(0, rows.size() - lastN);

        return rows.subList(from, rows.size()).stream()
                .map(row -> (Message) switch (row.getRole()) {
                    case "ASSISTANT" -> new AssistantMessage(row.getContent());
                    case "SYSTEM" -> new SystemMessage(row.getContent());
                    default -> new UserMessage(row.getContent());
                })
                .toList();
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        try {
            conversationRepository.deleteByConversationId(UUID.fromString(conversationId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid conversation id for clear: {}", conversationId);
        }
    }
}
