package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    List<ValidationResult> findByWorkflowIdOrderByCreatedAtDesc(UUID workflowId);

    List<ValidationResult> findByWorkflowIdAndValidationTypeOrderByCreatedAtDesc(UUID workflowId, String validationType);
}
