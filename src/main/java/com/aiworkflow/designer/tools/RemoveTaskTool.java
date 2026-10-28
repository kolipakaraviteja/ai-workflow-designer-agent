package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Removes a task node. Auto-reconnects only in the trivially safe case —
 * exactly one incoming and one outgoing flow — where the new flow inherits the
 * INCOMING flow's condition and default-flow flag so branch semantics survive.
 * Anything more complex is refused: the caller must rewire the graph
 * explicitly first, so no conditional path is ever silently dropped.
 */
@Slf4j
@Component
public class RemoveTaskTool implements WorkflowTool {

    @Override
    public String getName() {
        return "remove_task";
    }

    @Override
    public String getDescription() {
        return "Remove a task node by id. Only allowed on task nodes with exactly one "
                + "incoming and one outgoing flow, or with no flows. For nodes on "
                + "branched paths, rewire the surrounding flows first.";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing remove_task tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String taskId = (String) parameters.get("taskId");

        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId is required");
        }

        WorkflowNode taskToRemove = workflow.getNodeById(taskId);
        if (taskToRemove == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!taskToRemove.isTask()) {
            throw new IllegalArgumentException(
                    "Node '" + taskId + "' is a " + taskToRemove.getType() + ", not a task — "
                            + "remove_task only deletes task nodes");
        }

        List<WorkflowEdge> incomingEdges = workflow.getEdgesToNode(taskId);
        List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(taskId);

        boolean simpleChain = incomingEdges.size() == 1 && outgoingEdges.size() == 1;
        boolean detached = incomingEdges.isEmpty() && outgoingEdges.isEmpty();
        boolean branchPoint = incomingEdges.size() > 1 || outgoingEdges.size() > 1;

        if (!simpleChain && !detached) {
            StringBuilder details = new StringBuilder();
            for (WorkflowEdge e : incomingEdges) {
                details.append("in:").append(e.getSource())
                        .append(e.hasCondition() ? "[cond=" + e.getCondition() + "]" : "")
                        .append(" ");
            }
            for (WorkflowEdge e : outgoingEdges) {
                details.append("out:").append(e.getTarget())
                        .append(e.hasCondition() ? "[cond=" + e.getCondition() + "]" : "")
                        .append(" ");
            }
            if (branchPoint) {
                throw new IllegalArgumentException(
                        "Task '" + taskId + "' sits on " + incomingEdges.size() + " incoming and "
                                + outgoingEdges.size() + " outgoing flows (" + details + "). "
                                + "Refusing to delete it because that would drop conditional paths. "
                                + "Rewire the surrounding flows with connect_nodes first, then retry.");
            }
            throw new IllegalArgumentException(
                    "Task '" + taskId + "' has " + incomingEdges.size() + " incoming and "
                            + outgoingEdges.size() + " outgoing flows (" + details + ") — "
                            + "cannot safely reconnect. Rewire the flows explicitly first.");
        }

        // Remove the task and its edges
        workflow.getNodes().remove(taskToRemove);
        workflow.getEdges().removeIf(edge ->
                edge.getSource().equals(taskId) || edge.getTarget().equals(taskId));

        boolean reconnected = false;
        if (simpleChain) {
            WorkflowEdge in = incomingEdges.get(0);
            WorkflowEdge out = outgoingEdges.get(0);
            GraphInvariants.requireNoDuplicateEdge(workflow, in.getSource(), out.getTarget());

            // Inherit the INCOMING edge's condition/type: the branch condition
            // lives on the edge that entered the removed node.
            WorkflowEdge newEdge = new WorkflowEdge(in.getSource(), out.getTarget(), in.getCondition());
            newEdge.setType(in.getType());
            workflow.addEdge(newEdge);
            reconnected = true;

            log.info("Reconnected {} -> {} preserving incoming condition '{}'",
                    in.getSource(), out.getTarget(), in.getCondition());
        }

        log.info("Removed task {} from workflow", taskToRemove.getName());
        return Map.of(
                "success", true,
                "taskId", taskId,
                "taskName", taskToRemove.getName(),
                "reconnected", reconnected
        );
    }
}
