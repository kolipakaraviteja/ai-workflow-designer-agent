package com.aiworkflow.designer.agent.service;

import com.aiworkflow.designer.agent.impl.ConsultantAgent;
import com.aiworkflow.designer.agent.impl.DesignerAgent;
import com.aiworkflow.designer.agent.impl.OptimizerAgent;
import com.aiworkflow.designer.agent.impl.OrchestratorAgent;
import com.aiworkflow.designer.agent.impl.RuntimeAgent;
import com.aiworkflow.designer.agent.impl.ValidatorAgent;
import com.aiworkflow.designer.agent.model.AgentState;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final OrchestratorAgent orchestratorAgent;
    private final ConsultantAgent consultantAgent;
    private final DesignerAgent designerAgent;
    private final ValidatorAgent validatorAgent;
    private final OptimizerAgent optimizerAgent;
    private final RuntimeAgent runtimeAgent;

    @PostConstruct
    public void initializeAgents() {
        // Register all specialized agents with the orchestrator
        orchestratorAgent.registerAgent(consultantAgent);
        orchestratorAgent.registerAgent(designerAgent);
        orchestratorAgent.registerAgent(validatorAgent);
        orchestratorAgent.registerAgent(optimizerAgent);
        orchestratorAgent.registerAgent(runtimeAgent);

        log.info("Agent service initialized with {} specialized agents", 5);
    }

    public AgentState processUserRequest(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("Processing user request via orchestrator ({} chars)", userMessage.length());
        return orchestratorAgent.process(userMessage, workflow, conversationId);
    }

    public AgentState processWithDesigner(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("Processing request via designer agent: {}", userMessage);
        return designerAgent.process(userMessage, workflow, conversationId);
    }

    public AgentState processWithValidator(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("Processing request via validator agent: {}", userMessage);
        return validatorAgent.process(userMessage, workflow, conversationId);
    }

    public AgentState processWithOptimizer(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("Processing request via optimizer agent: {}", userMessage);
        return optimizerAgent.process(userMessage, workflow, conversationId);
    }

    public AgentState processWithRuntime(String userMessage, WorkflowDefinition workflow, String conversationId) {
        log.info("Processing request via runtime agent: {}", userMessage);
        return runtimeAgent.process(userMessage, workflow, conversationId);
    }

}
