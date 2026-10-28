package com.aiworkflow.designer.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_execution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "agent_path", length = 500)
    private String agentPath;

    @Column(nullable = false)
    private String agentType; // ORCHESTRATOR, DESIGNER, VALIDATOR, OPTIMIZER, RUNTIME

    @Column(nullable = false)
    private String action;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String input;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String output;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String toolCalls;

    @Column(nullable = false)
    @Builder.Default
    private String status = "COMPLETED"; // STARTED, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
        completedAt = LocalDateTime.now();
    }
}
