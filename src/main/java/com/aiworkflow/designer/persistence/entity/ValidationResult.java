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
@Table(name = "validation_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(nullable = false)
    private String validationType; // STRUCTURAL, LOGICAL, BUSINESS

    @Column(nullable = false)
    @Builder.Default
    private Boolean isValid = true;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String errors;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String warnings;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private String suggestions;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
