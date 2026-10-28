package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.conversation.service.ConversationService;
import com.aiworkflow.designer.persistence.entity.Conversation;
import com.aiworkflow.designer.persistence.entity.WorkflowSpec;
import com.aiworkflow.designer.requirements.service.RequirementSpecService;
import com.aiworkflow.designer.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final RequirementSpecService specService;
    private final AccessControlService accessControl;

    @GetMapping
    public ResponseEntity<List<UUID>> listConversations(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = accessControl.currentUserId(jwt);
        return ResponseEntity.ok(conversationService.listConversationIds(userId));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<Conversation>> getMessages(@PathVariable UUID conversationId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireConversationAccess(conversationId, jwt);
        return ResponseEntity.ok(conversationService.getHistory(conversationId));
    }

    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<Conversation>> getWorkflowMessages(@PathVariable UUID workflowId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireWorkflowAccess(workflowId, jwt);
        return ResponseEntity.ok(conversationService.getWorkflowHistory(workflowId));
    }

    @GetMapping("/workflow/{workflowId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestConversation(@PathVariable UUID workflowId,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireWorkflowAccess(workflowId, jwt);
        UUID userId = accessControl.currentUserId(jwt);

        return conversationService.findLatestConversationId(workflowId, userId)
                .map(conversationId -> ResponseEntity.ok(Map.of(
                        "conversationId", conversationId,
                        "spec", specService.findByConversationId(conversationId).orElse(null))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{conversationId}/spec")
    public ResponseEntity<WorkflowSpec> getSpec(@PathVariable UUID conversationId,
                                              @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireConversationAccess(conversationId, jwt);
        return specService.findByConversationId(conversationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID conversationId,
                                                   @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireConversationAccess(conversationId, jwt);
        conversationService.deleteConversation(conversationId);
        return ResponseEntity.noContent().build();
    }
}
