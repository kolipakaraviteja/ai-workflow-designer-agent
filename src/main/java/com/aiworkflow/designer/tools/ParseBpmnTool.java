package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.bpmn.BpmnParser;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseBpmnTool implements WorkflowTool {

    private final BpmnParser bpmnParser;

    @Override
    public String getName() {
        return "parse_bpmn";
    }

    @Override
    public String getDescription() {
        return "Parse existing BPMN XML and convert it to the internal workflow model";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing parse_bpmn tool");

        String bpmnXml = (String) parameters.get("bpmnXml");
        
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            throw new IllegalArgumentException("bpmnXml parameter is required");
        }

        WorkflowDefinition parsedWorkflow = bpmnParser.parseBpmnXml(bpmnXml);

        log.info("BPMN XML parsed successfully. Nodes: {}, Edges: {}", 
                parsedWorkflow.getNodes().size(), parsedWorkflow.getEdges().size());
        
        return Map.of(
                "success", true,
                "workflow", parsedWorkflow,
                "nodeCount", parsedWorkflow.getNodes().size(),
                "edgeCount", parsedWorkflow.getEdges().size(),
                "workflowName", parsedWorkflow.getName()
        );
    }
}
