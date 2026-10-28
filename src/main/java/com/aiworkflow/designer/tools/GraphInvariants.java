package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central graph invariants enforced by every mutating tool. A workflow model
 * must always satisfy: unique node IDs, unique edge IDs, edge endpoints that
 * exist, no duplicate (source,target) sequence flows, and node types from the
 * supported set.
 */
public final class GraphInvariants {

    public static final Set<String> TASK_TYPES = Set.of(
            "USER_TASK", "SERVICE_TASK", "SCRIPT_TASK", "BUSINESS_RULE_TASK",
            "SEND_TASK", "RECEIVE_TASK", "MANUAL_TASK");

    public static final Set<String> GATEWAY_TYPES = Set.of(
            "EXCLUSIVE_GATEWAY", "PARALLEL_GATEWAY", "INCLUSIVE_GATEWAY",
            "EVENT_BASED_GATEWAY", "COMPLEX_GATEWAY");

    public static final Set<String> EVENT_TYPES = Set.of(
            "START_EVENT", "END_EVENT", "TIMER_EVENT", "MESSAGE_EVENT",
            "ERROR_EVENT", "SIGNAL_EVENT", "INTERMEDIATE_EVENT");

    private GraphInvariants() {
    }

    public static Set<String> allowedTypesFor(String category) {
        return switch (category) {
            case "task" -> TASK_TYPES;
            case "gateway" -> GATEWAY_TYPES;
            case "event" -> EVENT_TYPES;
            default -> Set.of();
        };
    }

    /**
     * Validates a node type against the allowed set for its category.
     *
     * @return the normalized type (trimmed, upper-case)
     * @throws IllegalArgumentException when the type is not supported
     */
    public static String requireValidType(String category, String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        Set<String> allowed = allowedTypesFor(category);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported " + category + " type: '" + type + "'. Allowed: " + allowed);
        }
        return normalized;
    }

    /** Fails when a node with this ID already exists. */
    public static void requireUniqueNodeId(WorkflowDefinition workflow, String nodeId) {
        if (workflow.getNodeById(nodeId) != null) {
            throw new IllegalArgumentException(
                    "A node with id '" + nodeId + "' already exists — choose a different id");
        }
    }

    /** Fails when an edge between these nodes already exists. */
    public static void requireNoDuplicateEdge(WorkflowDefinition workflow, String sourceId, String targetId) {
        boolean duplicate = workflow.getEdges().stream()
                .anyMatch(e -> e.getSource().equals(sourceId) && e.getTarget().equals(targetId));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "A sequence flow from '" + sourceId + "' to '" + targetId
                            + "' already exists — to add or change its condition use "
                            + "add_condition(sourceId, targetId, condition)");
        }
    }

    /** Fails when an edge ID is already in use. */
    public static void requireUniqueEdgeId(WorkflowDefinition workflow, String edgeId) {
        boolean exists = workflow.getEdges().stream().anyMatch(e -> edgeId.equals(e.getId()));
        if (exists) {
            throw new IllegalArgumentException("An edge with id '" + edgeId + "' already exists");
        }
    }

    /** Fails when source or target node does not exist. */
    public static void requireEndpointNodes(WorkflowDefinition workflow, String sourceId, String targetId) {
        if (workflow.getNodeById(sourceId) == null) {
            throw new IllegalArgumentException("Source node not found: " + sourceId);
        }
        if (workflow.getNodeById(targetId) == null) {
            throw new IllegalArgumentException("Target node not found: " + targetId);
        }
    }

    /**
     * Full-model invariant check — used after a mutating tool runs to catch
     * corruption early. Returns the list of violations (empty when the graph
     * is clean).
     */
    public static List<String> check(WorkflowDefinition workflow) {
        List<String> violations = new java.util.ArrayList<>();

        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                violations.add("Node '" + node.getName() + "' has no id");
            } else if (!nodeIds.add(node.getId())) {
                violations.add("Duplicate node id: " + node.getId());
            }
            if (node.getType() == null
                    || (!TASK_TYPES.contains(node.getType())
                    && !GATEWAY_TYPES.contains(node.getType())
                    && !EVENT_TYPES.contains(node.getType()))) {
                violations.add("Node '" + node.getId() + "' has unsupported type: " + node.getType());
            }
        }

        Set<String> edgeIds = new HashSet<>();
        Set<String> edgePairs = new HashSet<>();
        for (WorkflowEdge edge : workflow.getEdges()) {
            if (edge.getId() != null && !edgeIds.add(edge.getId())) {
                violations.add("Duplicate edge id: " + edge.getId());
            }
            if (!nodeIds.contains(edge.getSource())) {
                violations.add("Edge '" + edge.getId() + "' has missing source node: " + edge.getSource());
            }
            if (!nodeIds.contains(edge.getTarget())) {
                violations.add("Edge '" + edge.getId() + "' has missing target node: " + edge.getTarget());
            }
            if (!edgePairs.add(edge.getSource() + "->" + edge.getTarget())) {
                violations.add("Duplicate sequence flow: " + edge.getSource() + " -> " + edge.getTarget());
            }
        }

        return violations;
    }
}
