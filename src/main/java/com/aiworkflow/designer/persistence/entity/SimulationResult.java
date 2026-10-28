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
@Table(name = "simulation_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputVariables;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String executionPath;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String gatewayDecisions;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String output;

    @Column(nullable = false)
    @Builder.Default
    private String status = "COMPLETED"; // STARTED, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
