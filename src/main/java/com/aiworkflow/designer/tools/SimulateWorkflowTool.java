package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.runtime.simulation.SimulationEngine;
import com.aiworkflow.designer.runtime.simulation.SimulationResult;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulateWorkflowTool implements WorkflowTool {

    private final SimulationEngine simulationEngine;

    @Override
    public String getName() {
        return "simulate_workflow";
    }

    @Override
    public String getDescription() {
        return "Run a simulation of the workflow with given input variables";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing simulate_workflow tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> inputVariables = (Map<String, Object>) parameters.getOrDefault("inputVariables", Map.of());

        SimulationResult result = simulationEngine.simulate(workflow, inputVariables);

        log.info("Workflow simulation completed. Success: {}", result.isSuccess());
        return Map.of(
                "success", true,
                "simulationResult", result,
                "executionPath", result.getExecutionPath(),
                "duration", result.getDuration(),
                "gatewayDecisions", result.getGatewayDecisions()
        );
    }
}
