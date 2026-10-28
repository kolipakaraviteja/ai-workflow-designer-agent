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
public class GetProcessInstanceTool implements WorkflowTool {

    private final FlowableProcessService flowableProcessService;

    @Override
    public String getName() {
        return "get_process_instance";
    }

    @Override
    public String getDescription() {
        return "Inspect a specific running process instance and its execution details";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing get_process_instance tool with parameters: {}", parameters);

        String processInstanceId = (String) parameters.get("processInstanceId");
        
        if (processInstanceId == null || processInstanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("processInstanceId parameter is required");
        }

        var processInstance = flowableProcessService.getProcessInstance(processInstanceId);

        log.info("Process instance retrieved: {}", processInstanceId);
        
        return Map.of(
                "success", true,
                "processInstanceId", processInstanceId,
                "processInstance", processInstance,
                "workflowName", workflow.getName()
        );
    }
}
