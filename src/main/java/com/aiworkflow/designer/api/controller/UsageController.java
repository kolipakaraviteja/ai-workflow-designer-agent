package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.persistence.entity.LlmUsage;
import com.aiworkflow.designer.security.AccessControlService;
import com.aiworkflow.designer.telemetry.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LLM token-usage reporting, scoped by caller. Users see only their own usage;
 * workflow usage requires workflow access; per-user lookup is admin-only.
 */
@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
@Slf4j
public class UsageController {

    private final TelemetryService telemetryService;
    private final AccessControlService accessControl;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myUsage(@AuthenticationPrincipal Jwt jwt) {
        String username = accessControl.requireUsername(jwt);
        List<LlmUsage> rows = telemetryService.usageForUsername(username);
        return ResponseEntity.ok(telemetryService.summarize(rows));
    }

    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<Map<String, Object>> workflowUsage(@PathVariable UUID workflowId,
                                                             @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireWorkflowAccess(workflowId, jwt);
        List<LlmUsage> rows = telemetryService.usageForWorkflow(workflowId);
        return ResponseEntity.ok(telemetryService.summarize(rows));
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<Map<String, Object>> userUsage(@PathVariable String username,
                                                         @AuthenticationPrincipal Jwt jwt) {
        accessControl.requireAdmin(jwt);
        List<LlmUsage> rows = telemetryService.usageForUsername(username);
        return ResponseEntity.ok(telemetryService.summarize(rows));
    }
}
