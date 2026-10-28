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
public class ExportWorkflowTool implements WorkflowTool {

    private final BpmnGenerator bpmnGenerator;

    @Override
    public String getName() {
        return "export_workflow";
    }

    @Override
    public String getDescription() {
        return "Export the workflow in various formats (BPMN XML, JSON)";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing export_workflow tool with parameters: {}", parameters);

        String format = (String) parameters.getOrDefault("format", "bpmn");
        
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String exportData;
        String mimeType;
        
        switch (format.toLowerCase()) {
            case "bpmn":
            case "xml":
                exportData = bpmnGenerator.generateBpmnXml(workflow);
                mimeType = "application/xml";
                break;
            case "json":
                exportData = workflow.toJson();
                mimeType = "application/json";
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format + ". Supported formats: bpmn, json");
        }

        log.info("Workflow exported successfully in {} format. Size: {} bytes", format, exportData.length());
        
        return Map.of(
                "success", true,
                "format", format,
                "data", exportData,
                "mimeType", mimeType,
                "size", exportData.length(),
                "workflowName", workflow.getName()
        );
    }
}
