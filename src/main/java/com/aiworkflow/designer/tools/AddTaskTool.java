package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AddTaskTool implements WorkflowTool {

    /** Optional task properties the caller may set at creation (same whitelist as update_task). */
    private static final java.util.List<String> TASK_PROPERTIES = java.util.List.of(
            "assignee", "candidateUsers", "candidateGroups",
            "serviceClass", "expression", "delegateExpression",
            "scriptFormat", "script");

    @Override
    public String getName() {
        return "add_task";
    }

    @Override
    public String getDescription() {
        return "Add a task to the workflow. USER_TASKs need assignee, candidateUsers or "
                + "candidateGroups; SERVICE_TASKs need serviceClass, expression or "
                + "delegateExpression; SCRIPT_TASKs need scriptFormat and script.";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing add_task tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String taskId = (String) parameters.get("taskId");
        String taskType = (String) parameters.get("taskType");
        String taskName = (String) parameters.get("taskName");

        if (taskType == null || taskType.trim().isEmpty()) {
            taskType = "USER_TASK";
        }
        taskType = GraphInvariants.requireValidType("task", taskType);

        if (taskId == null || taskId.trim().isEmpty()) {
            taskId = "task_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        GraphInvariants.requireUniqueNodeId(workflow, taskId);

        if (taskName == null || taskName.trim().isEmpty()) {
            taskName = "New Task";
        }

        WorkflowNode task = new WorkflowNode(taskId, taskType, taskName);

        for (String key : TASK_PROPERTIES) {
            Object value = parameters.get(key);
            if (value != null && !value.toString().isBlank()) {
                task.setProperty(key, value);
            }
        }

        workflow.addNode(task);

        log.info("Added task {} to workflow", taskName);
        return Map.of(
                "success", true,
                "taskId", taskId,
                "taskName", taskName,
                "taskType", taskType
        );
    }
}
