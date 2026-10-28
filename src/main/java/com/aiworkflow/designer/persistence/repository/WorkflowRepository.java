package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByName(String name);

    List<Workflow> findByStatus(String status);

    @Query("SELECT w FROM Workflow w WHERE w.createdBy = :username ORDER BY w.updatedAt DESC")
    List<Workflow> findByCreatedByOrderByUpdatedAtDesc(@Param("username") String username);

    @Query("SELECT w FROM Workflow w WHERE w.createdBy = :username AND w.status = :status ORDER BY w.updatedAt DESC")
    List<Workflow> findByCreatedByAndStatusOrderByUpdatedAtDesc(@Param("username") String username,
                                                              @Param("status") String status);

    @Query("SELECT w FROM Workflow w WHERE w.name LIKE %:keyword% OR w.description LIKE %:keyword%")
    List<Workflow> searchByKeyword(@Param("keyword") String keyword);
}
