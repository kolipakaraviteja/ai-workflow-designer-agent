package com.aiworkflow.designer.security;

import com.aiworkflow.designer.api.error.ResourceNotFoundException;
import com.aiworkflow.designer.persistence.entity.AppUser;
import com.aiworkflow.designer.persistence.entity.Conversation;
import com.aiworkflow.designer.persistence.entity.Deployment;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.persistence.repository.AppUserRepository;
import com.aiworkflow.designer.persistence.repository.ConversationRepository;
import com.aiworkflow.designer.persistence.repository.DeploymentRepository;
import com.aiworkflow.designer.persistence.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Central resource-owner authorization. Every endpoint that loads a resource
 * by ID must go through here: admins bypass ownership checks, non-owners get a
 * uniform 404 so resource IDs cannot be enumerated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessControlService {

    private final WorkflowRepository workflowRepository;
    private final DeploymentRepository deploymentRepository;
    private final ConversationRepository conversationRepository;
    private final AppUserRepository appUserRepository;

    public String currentUsername(Jwt jwt) {
        return jwt != null ? jwt.getSubject() : null;
    }

    public UUID currentUserId(Jwt jwt) {
        String username = currentUsername(jwt);
        if (username == null) {
            return null;
        }
        return appUserRepository.findByUsername(username)
                .map(AppUser::getId)
                .orElse(null);
    }

    public boolean isAdmin(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        String scope = jwt.getClaimAsString("scope");
        return scope != null && scope.contains("ROLE_ADMIN");
    }

    public String requireUsername(Jwt jwt) {
        String username = currentUsername(jwt);
        if (username == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return username;
    }

    public void requireAdmin(Jwt jwt) {
        if (!isAdmin(jwt)) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    /**
     * Loads a workflow and verifies the caller may access it.
     *
     * @throws ResourceNotFoundException when the workflow is missing or owned by someone else
     */
    public Workflow requireWorkflowAccess(UUID workflowId, Jwt jwt) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));
        if (owns(workflow.getCreatedBy(), jwt)) {
            return workflow;
        }
        throw new ResourceNotFoundException("Workflow not found: " + workflowId);
    }

    /**
     * Loads a deployment and verifies the caller owns the underlying workflow.
     */
    public Deployment requireDeploymentAccess(UUID deploymentId, Jwt jwt) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        Workflow workflow = deployment.getWorkflow();
        if (workflow != null && owns(workflow.getCreatedBy(), jwt)) {
            return deployment;
        }
        throw new ResourceNotFoundException("Deployment not found: " + deploymentId);
    }

    /**
     * Verifies the caller may see a conversation. Admin bypasses; otherwise the
     * conversation must contain at least one message attributed to the caller.
     * Conversations recorded before user attribution are only visible to admins
     * or the workflow owner.
     */
    public void requireConversationAccess(UUID conversationId, Jwt jwt) {
        if (isAdmin(jwt)) {
            return;
        }
        UUID userId = currentUserId(jwt);
        List<Conversation> messages = conversationRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            throw new ResourceNotFoundException("Conversation not found: " + conversationId);
        }
        boolean mine = messages.stream()
                .anyMatch(m -> userId != null && userId.equals(m.getUserId()));
        if (mine) {
            return;
        }
        UUID workflowId = messages.get(0).getWorkflowId();
        if (workflowId != null) {
            Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
            if (workflow != null && owns(workflow.getCreatedBy(), jwt)) {
                return;
            }
        }
        throw new ResourceNotFoundException("Conversation not found: " + conversationId);
    }

    private boolean owns(String createdBy, Jwt jwt) {
        if (isAdmin(jwt)) {
            return true;
        }
        String username = currentUsername(jwt);
        return username != null && username.equals(createdBy);
    }
}
