package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Safe in-place update of an existing task node: changes whitelisted fields
 * while preserving the node ID and every incoming/outgoing edge. This is the
 * correct way to change a task's type or properties — never remove and
 * recreate a node for a simple edit.
 */
@Slf4j
@Component
public class UpdateTaskTool implements WorkflowTool {

    /** Properties that only make sense on user tasks. */
    private static final Set<String> USER_TASK_ONLY = Set.of(
            "assignee", "candidateUsers", "candidateGroups");

    /** Properties that only make sense on service tasks. */
    private static final Set<String> SERVICE_TASK_ONLY = Set.of(
            "serviceClass", "expression", "delegateExpression");

    /** Properties that only make sense on script tasks. */
    private static final Set<String> SCRIPT_TASK_ONLY = Set.of(
            "scriptFormat", "script");

    @Override
    public String getName() {
        return "update_task";
    }

    @Override
    public String getDescription() {
        return "Update an existing task's type, name, assignee or implementation in place "
                + "(preserves its id and all flows). Use this instead of removing and "
                + "recreating a task.";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing update_task tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String taskId = (String) parameters.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }

        WorkflowNode node = workflow.getNodeById(taskId);
        if (node == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!node.isTask()) {
            throw new IllegalArgumentException(
                    "Node '" + taskId + "' is a " + node.getType() + ", not a task — "
                            + "update_task only modifies task nodes");
        }

        String newType = (String) parameters.get("taskType");
        if (newType != null && !newType.isBlank()) {
            newType = GraphInvariants.requireValidType("task", newType);
        }

        Map<String, Object> updates = new LinkedHashMap<>();

        String name = (String) parameters.get("taskName");
        if (name != null && !name.isBlank()) {
            node.setName(name.trim());
            updates.put("taskName", node.getName());
        }

        copyIfPresent(parameters, node, "assignee", updates);
        copyIfPresent(parameters, node, "candidateUsers", updates);
        copyIfPresent(parameters, node, "candidateGroups", updates);
        copyIfPresent(parameters, node, "serviceClass", updates);
        copyIfPresent(parameters, node, "expression", updates);
        copyIfPresent(parameters, node, "delegateExpression", updates);
        copyIfPresent(parameters, node, "scriptFormat", updates);
        copyIfPresent(parameters, node, "script", updates);

        if (newType != null && !newType.equals(node.getType())) {
            String oldType = node.getType();
            node.setType(newType);
            clearPropertiesForType(node);
            updates.put("taskType", newType);
            updates.put("previousType", oldType);
        }

        if (updates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nothing to update — provide at least one of: taskType, taskName, "
                            + "assignee, candidateUsers, candidateGroups, serviceClass, "
                            + "expression, delegateExpression, scriptFormat, script");
        }

        log.info("Updated task {} ({})", node.getId(), updates.keySet());
        return Map.of(
                "success", true,
                "taskId", node.getId(),
                "updated", updates
        );
    }

    private void copyIfPresent(Map<String, Object> parameters, WorkflowNode node,
                               String key, Map<String, Object> updates) {
        Object value = parameters.get(key);
        if (value != null && !value.toString().isBlank()) {
            node.setProperty(key, value);
            updates.put(key, value);
        }
    }

    /**
     * After a type change, removes properties that are invalid for the new
     * type so the node never carries stale configuration.
     */
    private void clearPropertiesForType(WorkflowNode node) {
        Set<String> toRemove = switch (node.getType()) {
            case "USER_TASK" -> union(SERVICE_TASK_ONLY, SCRIPT_TASK_ONLY);
            case "SERVICE_TASK" -> union(USER_TASK_ONLY, SCRIPT_TASK_ONLY);
            case "SCRIPT_TASK" -> union(USER_TASK_ONLY, SERVICE_TASK_ONLY);
            default -> union(USER_TASK_ONLY, union(SERVICE_TASK_ONLY, SCRIPT_TASK_ONLY));
        };
        toRemove.forEach(key -> node.getProperties().remove(key));
    }

    private Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new java.util.HashSet<>(a);
        out.addAll(b);
        return out;
    }
}
