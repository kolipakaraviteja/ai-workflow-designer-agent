package com.aiworkflow.designer.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "bpmn_knowledge")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmnKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "knowledge_type", nullable = false)
    private String knowledgeType; // SPECIFICATION, PATTERN, BEST_PRACTICE, DOCUMENTATION

    @Column
    private String source;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Document toDocument() {
        Map<String, Object> docMetadata = new java.util.HashMap<>();
        docMetadata.put("id", id.toString());
        docMetadata.put("knowledgeType", knowledgeType);
        if (source != null) {
            docMetadata.put("source", source);
        }
        docMetadata.put("metadata", metadata != null ? metadata : Map.of());
        return new Document(id.toString(), content, docMetadata);
    }
}
