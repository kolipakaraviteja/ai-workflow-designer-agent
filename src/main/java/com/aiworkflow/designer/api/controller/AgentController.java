package com.aiworkflow.designer.api.controller;

import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.agent.service.AgentService;
import com.aiworkflow.designer.api.dto.AgentRequest;
import com.aiworkflow.designer.api.dto.AgentResponse;
import com.aiworkflow.designer.conversation.memory.ConversationContext;
import com.aiworkflow.designer.conversation.service.ConversationService;
import com.aiworkflow.designer.persistence.entity.AgentExecution;
import com.aiworkflow.designer.persistence.entity.Workflow;
import com.aiworkflow.designer.persistence.repository.AgentExecutionRepository;
import com.aiworkflow.designer.requirements.service.RequirementSpecService;
import com.aiworkflow.designer.security.AccessControlService;
import com.aiworkflow.designer.telemetry.RequestTelemetry;
import com.aiworkflow.designer.telemetry.TelemetryService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final AgentService agentService;
    private final WorkflowService workflowService;
    private final ConversationService conversationService;
    private final AgentExecutionRepository agentExecutionRepository;
    private final RequirementSpecService specService;
    private final AccessControlService accessControl;
    private final TelemetryService telemetryService;

    @PostMapping("/process")
    public ResponseEntity<AgentResponse> processRequest(@RequestBody AgentRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        log.info("Processing agent request: agentType={}, workflowId={}",
                request.getAgentType(), request.getWorkflowId());

        long startedAt = System.currentTimeMillis();
        UUID conversationId = request.getConversationId() != null
                ? request.getConversationId()
                : conversationService.createConversationId();
        String conversationKey = conversationId.toString();

        try {
            // Get workflow — the caller must own it before an agent may mutate it
            Workflow workflow = null;
            WorkflowDefinition definition = null;

            if (request.getWorkflowId() != null) {
                workflow = accessControl.requireWorkflowAccess(request.getWorkflowId(), jwt);
                definition = workflowService.deserializeDefinition(workflow.getDefinitionJson());
            }

            UUID userId = accessControl.currentUserId(jwt);
            ConversationContext.set(request.getWorkflowId(), userId);
            RequestTelemetry.setContext(userId, accessControl.currentUsername(jwt),
                    request.getWorkflowId(), conversationId);

            // Process with appropriate agent
            AgentState agentState;
            if (request.getAgentType() != null && !request.getAgentType().isEmpty()) {
                agentState = switch (request.getAgentType().toUpperCase()) {
                    case "DESIGNER" -> agentService.processWithDesigner(request.getMessage(), definition, conversationKey);
                    case "VALIDATOR" -> agentService.processWithValidator(request.getMessage(), definition, conversationKey);
                    case "OPTIMIZER" -> agentService.processWithOptimizer(request.getMessage(), definition, conversationKey);
                    case "RUNTIME" -> agentService.processWithRuntime(request.getMessage(), definition, conversationKey);
                    default -> agentService.processUserRequest(request.getMessage(), definition, conversationKey);
                };
            } else {
                agentState = agentService.processUserRequest(request.getMessage(), definition, conversationKey);
            }

            // Get modified workflow from agent state
            WorkflowDefinition modifiedWorkflow = (WorkflowDefinition) agentState.getContext("modified_workflow");

            // Persist definition + BPMN + spec status atomically when the agent mutated the workflow.
            // Semantic validation issues are saved (status=INVALID) and surfaced in the
            // response rather than failing the request — deployment is the strict gate.
            String bpmnXml = null;
            WorkflowService.AgentPersistResult persisted = null;
            if (modifiedWorkflow != null) {
                persisted = workflowService.persistAgentResult(
                        workflow != null ? workflow.getId() : null,
                        modifiedWorkflow,
                        accessControl.requireUsername(jwt),
                        conversationId,
                        Boolean.TRUE.equals(agentState.getContext("spec_confirmed")));
                workflow = persisted.workflow();
                bpmnXml = workflow.getBpmnXml();
            }

            Object spec = agentState.getContext("spec");
            if (spec == null) {
                spec = specService.findByConversationId(conversationId).orElse(null);
            }

            recordExecution(request, agentState, startedAt);

            RequestTelemetry.Snapshot telemetry = RequestTelemetry.snapshot();
            telemetryService.persistLlmUsage(telemetry);

            String message = agentState.getLastAgentResponse();
            if (persisted != null && persisted.validation().hasErrors()) {
                message = (message != null ? message : "")
                        + "\n\nThe workflow was saved as a draft but has validation errors "
                        + "that must be fixed before deployment:\n- "
                        + String.join("\n- ", persisted.validation().getErrors());
            }

            AgentResponse response = AgentResponse.builder()
                    .agentType(agentState.getAgentType())
                    .status(agentState.getStatus())
                    .message(message)
                    .conversationId(conversationId)
                    .agentState(agentState)
                    .modifiedWorkflow(modifiedWorkflow)
                    .workflowId(workflow != null ? workflow.getId() : null)
                    .bpmnXml(bpmnXml)
                    .workflowSpec(spec)
                    .correlationId(telemetry.correlationId())
                    .agentPath(telemetry.agentPathString())
                    .toolCallCount(telemetry.toolCallCount())
                    .tokenUsage(telemetryService.requestSummary(telemetry))
                    .validationStatus(persisted != null && persisted.validation().getStatus() != null
                            ? persisted.validation().getStatus().name() : null)
                    .validationErrors(persisted != null ? persisted.validation().getErrors() : null)
                    .validationWarnings(persisted != null ? persisted.validation().getWarnings() : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Agent processing failed", e);

            AgentResponse response = AgentResponse.builder()
                    .agentType(request.getAgentType())
                    .status("FAILED")
                    .message("Processing failed.")
                    .conversationId(conversationId)
                    .correlationId(RequestTelemetry.correlationId())
                    .error("Processing failed")
                    .build();

            return ResponseEntity.internalServerError().body(response);
        } finally {
            ConversationContext.clear();
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Chat request ({} chars)", request.getMessage() != null ? request.getMessage().length() : 0);

        // Use orchestrator for chat requests
        request.setAgentType("ORCHESTRATOR");
        return processRequest(request, jwt);
    }

    private void recordExecution(AgentRequest request, AgentState agentState, long startedAt) {
        try {
            RequestTelemetry.Snapshot telemetry = RequestTelemetry.snapshot();
            AgentExecution execution = new AgentExecution();
            execution.setWorkflowId(request.getWorkflowId());
            execution.setCorrelationId(telemetry.correlationId());
            execution.setAgentPath(telemetry.agentPathString());
            execution.setAgentType(agentState.getAgentType());
            execution.setAction(request.getMessage() != null && request.getMessage().length() > 255
                    ? request.getMessage().substring(0, 255)
                    : request.getMessage());
            execution.setStatus(agentState.getStatus());
            execution.setDurationMs(System.currentTimeMillis() - startedAt);
            try {
                execution.setToolCalls(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(telemetry.toolCalls()));
            } catch (Exception jsonError) {
                log.debug("Could not serialize tool trace: {}", jsonError.getMessage());
            }
            Object error = agentState.getContext("error");
            if (error != null) {
                execution.setErrorMessage(error.toString());
            }
            agentExecutionRepository.save(execution);
        } catch (Exception e) {
            log.warn("Failed to record agent execution: {}", e.getMessage());
        }
    }
}
