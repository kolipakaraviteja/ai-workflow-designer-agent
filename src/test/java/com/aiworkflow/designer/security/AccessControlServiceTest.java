package com.aiworkflow.designer.security;

import com.aiworkflow.designer.api.error.ResourceNotFoundException;
import com.aiworkflow.designer.persistence.entity.AppUser;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.persistence.repository.AppUserRepository;
import com.aiworkflow.designer.persistence.repository.ConversationRepository;
import com.aiworkflow.designer.persistence.repository.DeploymentRepository;
import com.aiworkflow.designer.persistence.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessControlServiceTest {

    private WorkflowRepository workflowRepository;
    private AppUserRepository appUserRepository;
    private AccessControlService accessControl;

    private UUID workflowId;
    private Workflow owned;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        accessControl = new AccessControlService(
                workflowRepository,
                mock(DeploymentRepository.class),
                mock(ConversationRepository.class),
                appUserRepository);

        workflowId = UUID.randomUUID();
        owned = Workflow.builder().id(workflowId).name("Mine").createdBy("alice").build();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(owned));

        AppUser alice = new AppUser();
        alice.setId(UUID.randomUUID());
        alice.setUsername("alice");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
    }

    private Jwt jwtFor(String username, String scope) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(username)
                .claim("scope", scope)
                .build();
    }

    @Test
    void ownerCanAccessOwnWorkflow() {
        assertThat(accessControl.requireWorkflowAccess(workflowId, jwtFor("alice", "ROLE_USER")))
                .isSameAs(owned);
    }

    @Test
    void nonOwnerGetsNotFoundSoIdsCannotBeEnumerated() {
        assertThatThrownBy(() -> accessControl.requireWorkflowAccess(workflowId, jwtFor("bob", "ROLE_USER")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminBypassesOwnership() {
        assertThat(accessControl.requireWorkflowAccess(workflowId, jwtFor("admin", "ROLE_ADMIN")))
                .isSameAs(owned);
    }

    @Test
    void missingWorkflowIsNotFoundForEveryone() {
        UUID unknown = UUID.randomUUID();
        when(workflowRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accessControl.requireWorkflowAccess(unknown, jwtFor("admin", "ROLE_ADMIN")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
