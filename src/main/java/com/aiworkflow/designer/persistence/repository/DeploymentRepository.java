package com.aiworkflow.designer.persistence.repository;

import com.aiworkflow.designer.persistence.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByWorkflow_IdOrderByDeployedAtDesc(UUID workflowId);

    List<Deployment> findByStatus(String status);

    Optional<Deployment> findByDeploymentId(String deploymentId);
}
