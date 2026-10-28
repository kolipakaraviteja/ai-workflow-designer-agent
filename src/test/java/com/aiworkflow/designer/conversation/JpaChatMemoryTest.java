package com.aiworkflow.designer.conversation;

import com.aiworkflow.designer.conversation.memory.JpaChatMemory;
import com.aiworkflow.designer.persistence.entity.Conversation;
import com.aiworkflow.designer.persistence.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaChatMemoryTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private JpaChatMemory chatMemory;

    @Test
    void addPersistsMessagesWithConversationId() {
        UUID conversationId = UUID.randomUUID();
        chatMemory.add(conversationId.toString(),
                List.of(new UserMessage("hello"), new AssistantMessage("hi there")));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        List<Conversation> saved = captor.getAllValues();
        assertThat(saved.get(0).getConversationId()).isEqualTo(conversationId);
        assertThat(saved.get(0).getRole()).isEqualTo("USER");
        assertThat(saved.get(0).getContent()).isEqualTo("hello");
        assertThat(saved.get(1).getRole()).isEqualTo("ASSISTANT");
        assertThat(saved.get(1).getContent()).isEqualTo("hi there");
    }

    @Test
    void getReturnsLastNMessages() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(
                        row(conversationId, "USER", "first"),
                        row(conversationId, "ASSISTANT", "second"),
                        row(conversationId, "USER", "third")));

        List<Message> messages = chatMemory.get(conversationId.toString(), 2);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("second");
        assertThat(messages.get(1).getContent()).isEqualTo("third");
    }

    @Test
    void getReturnsEmptyForInvalidId() {
        assertThat(chatMemory.get("not-a-uuid", 10)).isEmpty();
    }

    private Conversation row(UUID conversationId, String role, String content) {
        return Conversation.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build();
    }
}
