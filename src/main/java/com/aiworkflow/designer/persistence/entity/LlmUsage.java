package com.aiworkflow.designer.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per LLM chat call, attributed to the requesting user and workflow.
 * Token fields may be null when the model does not report usage (local models).
 */
@Entity
@Table(name = "llm_usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 255)
    private String username;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "agent_type", length = 100)
    private String agentType;

    @Column(length = 255)
    private String model;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Column(name = "completion_tokens")
    private Long completionTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
