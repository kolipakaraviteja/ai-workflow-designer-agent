package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.bpmn.BpmnValidator;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeWorkflowTool implements WorkflowTool {

    private final BpmnValidator bpmnValidator;

    @Override
    public String getName() {
        return "analyze_workflow";
    }

    @Override
    public String getDescription() {
        return "Analyze the workflow structure and business logic for potential issues and optimizations";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing analyze_workflow tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        List<String> analysis = new ArrayList<>();
        
        // Basic structure analysis
        analysis.add("Workflow Analysis:");
        analysis.add(String.format("- Total nodes: %d", workflow.getNodes().size()));
        analysis.add(String.format("- Total edges: %d", workflow.getEdges().size()));
        
        // Count node types
        long startEvents = workflow.getNodes().stream().filter(n -> n.getType().equals("START_EVENT")).count();
        long endEvents = workflow.getNodes().stream().filter(n -> n.getType().equals("END_EVENT")).count();
        long userTasks = workflow.getNodes().stream().filter(n -> n.getType().equals("USER_TASK")).count();
        long serviceTasks = workflow.getNodes().stream().filter(n -> n.getType().equals("SERVICE_TASK")).count();
        long gateways = workflow.getNodes().stream().filter(n -> n.getType().contains("GATEWAY")).count();
        
        analysis.add(String.format("- Start events: %d", startEvents));
        analysis.add(String.format("- End events: %d", endEvents));
        analysis.add(String.format("- User tasks: %d", userTasks));
        analysis.add(String.format("- Service tasks: %d", serviceTasks));
        analysis.add(String.format("- Gateways: %d", gateways));
        
        // Validation analysis
        BpmnValidator.ValidationResult validationResult = bpmnValidator.validate(workflow);
        if (validationResult.isValid()) {
            analysis.add("- Structural validation: PASSED");
        } else {
            analysis.add("- Structural validation: FAILED");
            if (validationResult.getErrors() != null) {
                validationResult.getErrors().forEach(error -> analysis.add("  Error: " + error));
            }
        }
        
        if (validationResult.getWarnings() != null && !validationResult.getWarnings().isEmpty()) {
            analysis.add("- Warnings detected:");
            validationResult.getWarnings().forEach(warning -> analysis.add("  Warning: " + warning));
        }
        
        // Business logic insights
        analysis.add("- Business Logic Insights:");
        
        if (gateways > 0) {
            analysis.add("  Workflow contains decision points - ensure conditions are properly defined");
        }
        
        if (userTasks > 5) {
            analysis.add("  Workflow has many user tasks - consider automation opportunities");
        }
        
        if (serviceTasks == 0) {
            analysis.add("  No service tasks found - workflow may be manual-heavy");
        }
        
        if (startEvents != 1) {
            analysis.add("  Warning: Workflow should have exactly one start event");
        }
        
        if (endEvents == 0) {
            analysis.add("  Warning: Workflow has no end events");
        }

        log.info("Workflow analysis completed");
        return Map.of(
                "success", true,
                "analysis", analysis,
                "validationResult", validationResult
        );
    }
}
