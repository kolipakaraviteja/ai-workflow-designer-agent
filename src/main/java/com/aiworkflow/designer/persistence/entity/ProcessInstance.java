package com.aiworkflow.designer.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "process_instance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id")
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @Column(nullable = false)
    private String engineProcessInstanceId;

    @Column(nullable = false)
    @Builder.Default
    private String status = "RUNNING"; // RUNNING, COMPLETED, FAILED, SUSPENDED

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> variables;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> currentActivity;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String executionPath;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }
}
