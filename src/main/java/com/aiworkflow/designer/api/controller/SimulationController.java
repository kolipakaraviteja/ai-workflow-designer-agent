package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.api.dto.SimulationRequest;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.runtime.simulation.SimulationEngine;
import com.aiworkflow.designer.runtime.simulation.SimulationResult;
import com.aiworkflow.designer.runtime.simulation.WhatIfResult;
import com.aiworkflow.designer.security.AccessControlService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Slf4j
public class SimulationController {

    private final SimulationEngine simulationEngine;
    private final WorkflowService workflowService;
    private final AccessControlService accessControl;

    @PostMapping("/simulate")
    public ResponseEntity<SimulationResult> simulate(@RequestBody SimulationRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("Simulating workflow: {}", request.getWorkflowId());

        Workflow workflow = accessControl.requireWorkflowAccess(request.getWorkflowId(), jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());

        SimulationResult result = simulationEngine.simulate(definition, request.getInputVariables());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/what-if")
    public ResponseEntity<WhatIfResult> whatIf(@RequestBody SimulationRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("What-if analysis for workflow: {}", request.getWorkflowId());

        Workflow workflow = accessControl.requireWorkflowAccess(request.getWorkflowId(), jwt);

        WorkflowDefinition definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());

        WhatIfResult result = simulationEngine.whatIf(definition, request.getInputVariables(),
                request.getModifiedVariables(), request.getChangeDescription());
        return ResponseEntity.ok(result);
    }
}
