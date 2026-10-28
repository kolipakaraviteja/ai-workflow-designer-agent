package com.aiworkflow.designer.bpmn;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class BpmnValidator {

    public ValidationResult validate(WorkflowDefinition workflow) {
        log.info("Validating workflow: {}", workflow.getName());

        ValidationResult result = new ValidationResult();

        // Structural validation
        validateStructure(workflow, result);

        // Logical validation
        validateLogic(workflow, result);

        // Business validation
        validateBusinessRules(workflow, result);

        // Determine overall status
        if (result.getErrors().isEmpty() && result.getWarnings().isEmpty()) {
            result.setStatus(ValidationStatus.VALID);
        } else if (!result.getErrors().isEmpty()) {
            result.setStatus(ValidationStatus.INVALID);
        } else {
            result.setStatus(ValidationStatus.WARNING);
        }

        log.info("Validation completed with status: {}", result.getStatus());
        return result;
    }

    private void validateStructure(WorkflowDefinition workflow, ValidationResult result) {
        log.debug("Validating structure");

        // Check for start event
        if (workflow.getStartEvent() == null) {
            result.addError("Missing start event - every workflow must have exactly one start event");
        } else if (workflow.getNodesByType("START_EVENT").size() > 1) {
            result.addError("Multiple start events found - workflow should have exactly one start event");
        }

        // Check for end events
        if (workflow.getEndEvents().isEmpty()) {
            result.addError("Missing end event - workflow must have at least one end event");
        }

        // Check for unreachable nodes
        List<String> reachableNodes = findReachableNodes(workflow);
        for (WorkflowNode node : workflow.getNodes()) {
            if (!reachableNodes.contains(node.getId())) {
                result.addError("Unreachable node detected: " + node.getName() + " - this node cannot be reached from the start event");
            }
        }

        // Check for dead-end paths
        for (WorkflowNode node : workflow.getNodes()) {
            if (!node.isEndEvent() && workflow.getEdgesFromNode(node.getId()).isEmpty()) {
                result.addError("Dead-end path detected at node: " + node.getName() + " - no outgoing sequence flows");
            }
        }

        // Check for broken flows
        for (WorkflowEdge edge : workflow.getEdges()) {
            if (workflow.getNodeById(edge.getSource()) == null) {
                result.addError("Broken sequence flow - source node not found: " + edge.getSource());
            }
            if (workflow.getNodeById(edge.getTarget()) == null) {
                result.addError("Broken sequence flow - target node not found: " + edge.getTarget());
            }
        }

        // Check for duplicate node/edge IDs and duplicate sequence flows
        validateUniqueness(workflow, result);

        // Check for orphaned nodes (no incoming edges except start event)
        for (WorkflowNode node : workflow.getNodes()) {
            if (!node.isStartEvent() && workflow.getEdgesToNode(node.getId()).isEmpty()) {
                result.addWarning("Node has no incoming flows: " + node.getName() + " - this might be intentional but should be reviewed");
            }
        }
    }

    private void validateLogic(WorkflowDefinition workflow, ValidationResult result) {
        log.debug("Validating logic");

        // Check gateway conditions
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isGateway()) {
                validateGateway(workflow, node, result);
            }
        }

        // Cycle analysis: rework loops are valid BPMN when they exit through a
        // conditional edge; only unconditional cycles are real infinite loops.
        validateCycles(workflow, result);

        // Check for incomplete conditional flows
        for (WorkflowEdge edge : workflow.getEdges()) {
            if (edge.hasCondition()) {
                // Basic check that condition is not empty
                if (edge.getCondition().trim().isEmpty()) {
                    result.addError("Empty condition expression on flow from " + edge.getSource() + " to " + edge.getTarget());
                } else if (!looksLikeExpression(edge.getCondition())) {
                    result.addWarning("Condition '" + edge.getCondition() + "' on flow " + edge.getSource()
                            + " -> " + edge.getTarget() + " is not an expression — it will be "
                            + "treated as a variable name at runtime");
                }
            }
        }
    }

    private void validateGateway(WorkflowDefinition workflow, WorkflowNode gateway, ValidationResult result) {
        List<WorkflowEdge> outgoingEdges = workflow.getEdgesFromNode(gateway.getId());
        List<WorkflowEdge> incomingEdges = workflow.getEdgesToNode(gateway.getId());

        // Check gateway type
        String gatewayType = gateway.getType();

        if ("EXCLUSIVE_GATEWAY".equals(gatewayType)) {
            validateExclusiveGateway(gateway, outgoingEdges, result);
        } else if ("PARALLEL_GATEWAY".equals(gatewayType)) {
            validateParallelGateway(gateway, outgoingEdges, incomingEdges, result);
        } else if ("INCLUSIVE_GATEWAY".equals(gatewayType)) {
            validateInclusiveGateway(gateway, outgoingEdges, result);
        }

        // Check for proper gateway usage
        if (outgoingEdges.size() < 2 && !gatewayType.equals("EVENT_BASED_GATEWAY")) {
            result.addWarning("Gateway " + gateway.getName() + " has only one outgoing flow - consider using a direct connection instead");
        }

        if (incomingEdges.size() < 2 && !gatewayType.equals("EVENT_BASED_GATEWAY")) {
            result.addWarning("Gateway " + gateway.getName() + " has only one incoming flow - consider using a direct connection instead");
        }
    }

    private void validateExclusiveGateway(WorkflowNode gateway, List<WorkflowEdge> outgoingEdges, ValidationResult result) {
        int conditionalFlows = 0;
        boolean hasDefaultFlow = false;

        for (WorkflowEdge edge : outgoingEdges) {
            if (edge.hasCondition()) {
                conditionalFlows++;
            }
            if (edge.isDefaultFlow()) {
                hasDefaultFlow = true;
            }
        }

        if (conditionalFlows == 0 && outgoingEdges.size() > 1) {
            result.addError("Exclusive gateway " + gateway.getName() + " has multiple outgoing flows but no conditions defined");
        }

        if (conditionalFlows < outgoingEdges.size() - 1 && !hasDefaultFlow) {
            result.addWarning("Exclusive gateway " + gateway.getName() + " may have incomplete conditions - consider adding a default flow");
        }

        if (hasDefaultFlow && conditionalFlows + 1 < outgoingEdges.size()) {
            result.addWarning("Exclusive gateway " + gateway.getName() + " has a default flow but still has unconditioned outgoing flows");
        }
    }

    private void validateParallelGateway(WorkflowNode gateway, List<WorkflowEdge> outgoingEdges,
                                         List<WorkflowEdge> incomingEdges, ValidationResult result) {
        // Parallel gateways should not have conditions
        for (WorkflowEdge edge : outgoingEdges) {
            if (edge.hasCondition()) {
                result.addError("Parallel gateway " + gateway.getName() + " has conditional flows - parallel gateways should not have conditions");
            }
        }

        // Check for proper parallel gateway pairs
        if (incomingEdges.size() > 1 && outgoingEdges.size() == 1) {
            // This is a converging parallel gateway
            result.addInfo("Converging parallel gateway detected: " + gateway.getName());
        } else if (outgoingEdges.size() > 1 && incomingEdges.size() == 1) {
            // This is a diverging parallel gateway
            result.addInfo("Diverging parallel gateway detected: " + gateway.getName());
        } else if (outgoingEdges.size() > 1 && incomingEdges.size() > 1) {
            // This might be both - could be a warning
            result.addWarning("Parallel gateway " + gateway.getName() + " has multiple incoming and outgoing flows - ensure this is intentional");
        }
    }

    private void validateInclusiveGateway(WorkflowNode gateway, List<WorkflowEdge> outgoingEdges, ValidationResult result) {
        int conditionalFlows = 0;

        for (WorkflowEdge edge : outgoingEdges) {
            if (edge.hasCondition()) {
                conditionalFlows++;
            }
        }

        if (conditionalFlows == 0 && outgoingEdges.size() > 1) {
            result.addError("Inclusive gateway " + gateway.getName() + " has multiple outgoing flows but no conditions defined");
        }

        if (conditionalFlows < outgoingEdges.size()) {
            result.addWarning("Inclusive gateway " + gateway.getName() + " has unconditioned flows - ensure this is intentional");
        }
    }

    private void validateBusinessRules(WorkflowDefinition workflow, ValidationResult result) {
        log.debug("Validating business rules");

        // Check for duplicate approvals
        List<WorkflowNode> approvalTasks = workflow.getNodes().stream()
                .filter(node -> node.getName().toLowerCase().contains("approval"))
                .toList();

        if (approvalTasks.size() > 2) {
            result.addWarning("Multiple approval tasks detected (" + approvalTasks.size() + ") - consider if this level of approval is necessary");
        }

        // Check for tasks without error handling
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isTask() && !hasErrorHandling(workflow, node)) {
                result.addWarning("Task " + node.getName() + " has no error handling - consider adding boundary events or try-catch logic");
            }
        }

        // Check for user tasks without assignee
        for (WorkflowNode node : workflow.getNodes()) {
            if ("USER_TASK".equals(node.getType())) {
                Object assignee = node.getProperty("assignee");
                Object candidateUsers = node.getProperty("candidateUsers");
                Object candidateGroups = node.getProperty("candidateGroups");

                if ((assignee == null || assignee.toString().trim().isEmpty()) &&
                    (candidateUsers == null || candidateUsers.toString().trim().isEmpty()) &&
                    (candidateGroups == null || candidateGroups.toString().trim().isEmpty())) {
                    result.addError("User task " + node.getName() + " has no assignee, candidate users, or candidate groups defined");
                }
            }
        }

        // Check for service tasks without implementation
        for (WorkflowNode node : workflow.getNodes()) {
            if ("SERVICE_TASK".equals(node.getType())) {
                Object serviceClass = node.getProperty("serviceClass");
                Object expression = node.getProperty("expression");
                Object delegateExpression = node.getProperty("delegateExpression");

                if ((serviceClass == null || serviceClass.toString().trim().isEmpty()) &&
                    (expression == null || expression.toString().trim().isEmpty()) &&
                    (delegateExpression == null || delegateExpression.toString().trim().isEmpty())) {
                    result.addError("Service task " + node.getName() + " has no implementation defined (class, expression, or delegate expression)");
                }
            }
        }

        // Check for long-running tasks without timeout
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.isTask() && node.getProperty("timeout") == null) {
                result.addInfo("Task " + node.getName() + " has no timeout configured - consider adding timeout for long-running tasks");
            }
        }

        // Check for missing SLA configuration
        boolean hasSla = workflow.getNodes().stream()
                .anyMatch(node -> node.getProperty("sla") != null);

        if (!hasSla && workflow.getNodes().size() > 5) {
            result.addInfo("Workflow has no SLA configuration - consider adding SLA monitoring for complex workflows");
        }
    }

    private List<String> findReachableNodes(WorkflowDefinition workflow) {
        List<String> reachable = new ArrayList<>();
        WorkflowNode startEvent = workflow.getStartEvent();

        if (startEvent != null) {
            traverseFromNode(workflow, startEvent.getId(), reachable, new HashSet<>());
        }

        return reachable;
    }

    private void traverseFromNode(WorkflowDefinition workflow, String nodeId, List<String> reachable, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return; // Prevent infinite loops
        }

        visited.add(nodeId);
        reachable.add(nodeId);

        for (WorkflowEdge edge : workflow.getEdgesFromNode(nodeId)) {
            traverseFromNode(workflow, edge.getTarget(), reachable, visited);
        }
    }

    private void validateUniqueness(WorkflowDefinition workflow, ValidationResult result) {
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.getId() != null && !nodeIds.add(node.getId())) {
                result.addError("Duplicate node id: " + node.getId());
            }
        }
        Set<String> edgeIds = new HashSet<>();
        Set<String> edgePairs = new HashSet<>();
        for (WorkflowEdge edge : workflow.getEdges()) {
            if (edge.getId() != null && !edgeIds.add(edge.getId())) {
                result.addError("Duplicate edge id: " + edge.getId());
            }
            if (!edgePairs.add(edge.getSource() + "->" + edge.getTarget())) {
                result.addError("Duplicate sequence flow: " + edge.getSource() + " -> " + edge.getTarget());
            }
        }
    }

    /**
     * A cycle is only an error when it cannot be exited: rework loops that
     * flow back through a conditional gateway edge are legitimate BPMN.
     */
    private void validateCycles(WorkflowDefinition workflow, ValidationResult result) {
        Set<String> cyclic = findCyclicNodes(workflow);
        if (cyclic.isEmpty()) {
            return;
        }

        // A cycle "has an exit" when some node inside it has a conditional or
        // default flow leaving to a node outside the cycle.
        boolean hasExit = cyclic.stream().anyMatch(nodeId ->
                workflow.getEdgesFromNode(nodeId).stream().anyMatch(edge ->
                        !cyclic.contains(edge.getTarget())
                                && (edge.hasCondition() || edge.isDefaultFlow())));

        if (hasExit) {
            result.addInfo("Workflow contains a rework loop with a conditional exit — "
                    + "verify the loop condition is correct");
        } else {
            result.addError("Unconditional cycle detected involving nodes " + cyclic
                    + " — this is an infinite loop with no exit condition");
        }
    }

    private Set<String> findCyclicNodes(WorkflowDefinition workflow) {
        Set<String> cyclic = new HashSet<>();
        for (WorkflowNode node : workflow.getNodes()) {
            if (reachesSelf(workflow, node.getId(), new HashSet<>())) {
                cyclic.add(node.getId());
            }
        }
        return cyclic;
    }

    private boolean reachesSelf(WorkflowDefinition workflow, String nodeId, Set<String> visited) {
        for (WorkflowEdge edge : workflow.getEdgesFromNode(nodeId)) {
            if (edge.getTarget().equals(nodeId) || visited.contains(edge.getTarget())) {
                if (edge.getTarget().equals(nodeId)) {
                    return true;
                }
                continue;
            }
            if (edge.getTarget().equals(nodeId)) {
                return true;
            }
            visited.add(edge.getTarget());
            if (reachesSelf(workflow, edge.getTarget(), visited)) {
                return true;
            }
        }
        return false;
    }

    /** Heuristic: is this condition already expression-shaped? */
    private boolean looksLikeExpression(String condition) {
        String c = condition.trim();
        return c.startsWith("${") || c.contains("==") || c.contains("!=")
                || c.contains(">=") || c.contains("<=") || c.contains("&&")
                || c.contains("||") || c.equals("true") || c.equals("false");
    }

    private boolean hasErrorHandling(WorkflowDefinition workflow, WorkflowNode node) {
        // Check if the node has any boundary events or error handling
        // For now, simple check - in production, would be more sophisticated
        return node.getProperty("errorHandler") != null;
    }

    public enum ValidationStatus {
        VALID, WARNING, INVALID
    }

    public static class ValidationResult {
        private ValidationStatus status;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> info = new ArrayList<>();

        public ValidationStatus getStatus() { return status; }
        public void setStatus(ValidationStatus status) { this.status = status; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getInfo() { return info; }

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public void addInfo(String info) {
            this.info.add(info);
        }

        public boolean isValid() {
            return status == ValidationStatus.VALID;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
