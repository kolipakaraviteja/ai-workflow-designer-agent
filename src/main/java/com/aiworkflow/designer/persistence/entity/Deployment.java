package com.aiworkflow.designer.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deployment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(nullable = false)
    private String deploymentId;

    /** The workflow version this deployment was built from. */
    @Column(name = "workflow_version")
    private Integer workflowVersion;

    @Column(nullable = false)
    @Builder.Default
    private String engine = "FLOWABLE"; // FLOWABLE, CAMUNDA, ACTIVITI

    @Column(nullable = false)
    @Builder.Default
    private String status = "DEPLOYED"; // DEPLOYED, FAILED, UNDEPLOYED

    @Column
    private String engineProcessDefinitionId;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime deployedAt;

    @Column
    private LocalDateTime undeployedAt;

    @Column(nullable = false)
    private String deployedBy;

    @PrePersist
    protected void onCreate() {
        deployedAt = LocalDateTime.now();
    }
}
