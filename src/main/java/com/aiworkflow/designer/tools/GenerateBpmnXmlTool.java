package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.bpmn.BpmnGenerator;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateBpmnXmlTool implements WorkflowTool {

    private final BpmnGenerator bpmnGenerator;

    @Override
    public String getName() {
        return "generate_bpmn_xml";
    }

    @Override
    public String getDescription() {
        return "Convert the workflow model to BPMN 2.0 XML format";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing generate_bpmn_xml tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String bpmnXml = bpmnGenerator.generateBpmnXml(workflow);

        log.info("BPMN XML generated successfully. Length: {} characters", bpmnXml.length());
        return Map.of(
                "success", true,
                "bpmnXml", bpmnXml,
                "length", bpmnXml.length(),
                "workflowName", workflow.getName()
        );
    }
}
