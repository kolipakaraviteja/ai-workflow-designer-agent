package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.DocumentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentSourceRepository extends JpaRepository<DocumentSource, UUID> {

    List<DocumentSource> findByPurposeOrderByCreatedAtDesc(String purpose);
}
