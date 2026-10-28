package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.runtime.flowable.FlowableProcessService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetProcessStatusTool implements WorkflowTool {

    private final FlowableProcessService flowableProcessService;

    @Override
    public String getName() {
        return "get_process_status";
    }

    @Override
    public String getDescription() {
        return "Query the runtime status of a deployed process";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing get_process_status tool with parameters: {}", parameters);

        String processDefinitionKey = (String) parameters.get("processDefinitionKey");
        
        if (processDefinitionKey == null || processDefinitionKey.trim().isEmpty()) {
            // Use workflow name as process definition key
            processDefinitionKey = workflow.getName();
        }

        var processStatus = flowableProcessService.getProcessDefinitionStatus(processDefinitionKey);

        log.info("Process status retrieved for: {}", processDefinitionKey);
        
        return Map.of(
                "success", true,
                "processDefinitionKey", processDefinitionKey,
                "status", processStatus,
                "workflowName", workflow.getName()
        );
    }
}
