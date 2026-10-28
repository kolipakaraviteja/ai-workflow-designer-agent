package com.aiworkflow.designer.runtime.simulation;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * An honest graph walkthrough. It traverses the workflow the way the engine
 * would, evaluating conditions only where {@link ConditionEvaluator} supports
 * the syntax. Conditions it cannot evaluate are recorded as UNEVALUATED and
 * never silently treated as true — when a branch cannot be resolved the
 * walkthrough either takes a declared default flow (and says so) or stops.
 */
@Slf4j
@Component
public class SimulationEngine {

    /** Hard bound on traversal steps so rework loops can't hang the request. */
    private static final int MAX_STEPS = 1000;
    /** A node may be revisited (rework loops) but only this many times. */
    private static final int MAX_NODE_VISITS = 20;

    public SimulationResult simulate(WorkflowDefinition workflow, Map<String, Object> inputVariables) {
        log.info("Simulating workflow: {} ({} variables)",
                workflow.getName(), inputVariables != null ? inputVariables.size() : 0);

        SimulationResult result = new SimulationResult();
        result.setWorkflowName(workflow.getName());
        result.setInputVariables(inputVariables != null ? new HashMap<>(inputVariables) : new HashMap<>());
        result.setStartTime(System.currentTimeMillis());

        try {
            Map<String, Object> variables = new HashMap<>(result.getInputVariables());

            WorkflowNode startEvent = workflow.getStartEvent();
            if (startEvent == null) {
                result.setSuccess(false);
                result.setErrorMessage("No start event found in workflow");
                result.setEndTime(System.currentTimeMillis());
                return result;
            }

            Deque<WorkflowNode> queue = new ArrayDeque<>();
            Map<String, Integer> visits = new HashMap<>();
            queue.add(startEvent);
            int steps = 0;

            while (!queue.isEmpty()) {
                if (++steps > MAX_STEPS) {
                    result.getNotes().add("Traversal stopped after " + MAX_STEPS
                            + " steps — the workflow likely contains an unbounded loop");
                    result.setSuccess(false);
                    result.setErrorMessage("Simulation exceeded the step limit");
                    break;
                }

                WorkflowNode current = queue.poll();
                int visitCount = visits.merge(current.getId(), 1, Integer::sum);
                if (visitCount > MAX_NODE_VISITS) {
                    result.getNotes().add("Node '" + current.getName() + "' was visited "
                            + MAX_NODE_VISITS + " times — loop traversal stopped");
                    continue;
                }

                log.debug("Simulating node {} ({})", current.getName(), current.getType());
                recordStep(result, current);
                simulateNode(current, variables);

                List<WorkflowEdge> outgoing = workflow.getEdgesFromNode(current.getId());
                if (current.isEndEvent() || outgoing.isEmpty()) {
                    continue;
                }

                List<WorkflowEdge> selected = selectOutgoing(workflow, current, outgoing, variables, result);
                for (WorkflowEdge edge : selected) {
                    WorkflowNode next = workflow.getNodeById(edge.getTarget());
                    if (next != null) {
                        queue.add(next);
                    }
                }
            }

            result.setOutputVariables(variables);
            if (result.getErrorMessage() == null) {
                result.setSuccess(true);
            }
            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());
            log.info("Simulation done: {} steps, path length {}", steps, result.getExecutionPath().size());
            return result;

        } catch (Exception e) {
            log.error("Simulation failed", e);
            result.setSuccess(false);
            result.setErrorMessage("Simulation failed: " + e.getMessage());
            result.setEndTime(System.currentTimeMillis());
            result.setDuration(result.getEndTime() - result.getStartTime());
            return result;
        }
    }

    private void recordStep(SimulationResult result, WorkflowNode node) {
        result.getExecutionPath().add(node.getId());
        result.getExecutionDetails().add(new ExecutionStep(
                node.getId(), node.getName(), node.getType(), "COMPLETED",
                System.currentTimeMillis()));
    }

    /** Marks simulated variable side-effects — bookkeeping only, not real execution. */
    private void simulateNode(WorkflowNode node, Map<String, Object> variables) {
        switch (node.getType()) {
            case "START_EVENT" -> {
                variables.put("processStarted", true);
                variables.put("startTime", System.currentTimeMillis());
            }
            case "END_EVENT" -> {
                variables.put("processCompleted", true);
                variables.put("endTime", System.currentTimeMillis());
            }
            default -> {
                variables.put(node.getId() + "_completed", true);
                variables.put(node.getId() + "_completedAt", System.currentTimeMillis());
            }
        }
    }

    /**
     * Chooses outgoing edges the way the engine type requires:
     * EXCLUSIVE picks the first TRUE conditional flow or the default;
     * PARALLEL takes everything; INCLUSIVE takes all TRUE flows (plus
     * unconditional ones). UNEVALUATED conditions are reported, never assumed.
     */
    private List<WorkflowEdge> selectOutgoing(WorkflowDefinition workflow, WorkflowNode node,
                                            List<WorkflowEdge> outgoing,
                                            Map<String, Object> variables,
                                            SimulationResult result) {
        if ("PARALLEL_GATEWAY".equals(node.getType())) {
            return outgoing;
        }

        if ("EXCLUSIVE_GATEWAY".equals(node.getType())) {
            return selectExclusive(node, outgoing, variables, result);
        }

        // Inclusive gateways and non-gateway nodes with several outgoing flows:
        // every TRUE flow is taken; unconditional flows too.
        List<WorkflowEdge> selected = new ArrayList<>();
        boolean sawUnevaluated = false;
        WorkflowEdge defaultFlow = null;
        for (WorkflowEdge edge : outgoing) {
            if (edge.isDefaultFlow()) {
                defaultFlow = edge;
                continue;
            }
            if (!edge.hasCondition()) {
                selected.add(edge);
                continue;
            }
            switch (evaluateAndRecord(node, edge, variables, result)) {
                case TRUE -> selected.add(edge);
                case UNEVALUATED -> sawUnevaluated = true;
                case FALSE -> { /* skipped */ }
            }
        }

        if (!selected.isEmpty()) {
            if (sawUnevaluated) {
                result.getNotes().add("Node '" + node.getName()
                        + "': some conditions could not be evaluated — only confirmed paths were traversed");
            }
            return selected;
        }
        if (sawUnevaluated) {
            result.getNotes().add("Node '" + node.getName()
                    + "': branch unresolved — condition(s) could not be evaluated");
        }
        if (defaultFlow != null) {
            result.getNotes().add("Node '" + node.getName()
                    + "': taking default flow" + (sawUnevaluated ? " because conditions were unevaluable" : ""));
            return List.of(defaultFlow);
        }
        if (outgoing.size() > 1 || sawUnevaluated) {
            result.getNotes().add("Node '" + node.getName()
                    + "': no outgoing path matched — traversal of this path ends here");
            return List.of();
        }
        return outgoing;
    }

    private List<WorkflowEdge> selectExclusive(WorkflowNode gateway, List<WorkflowEdge> outgoing,
                                              Map<String, Object> variables,
                                              SimulationResult result) {
        WorkflowEdge defaultFlow = null;
        WorkflowEdge firstUnevaluated = null;

        for (WorkflowEdge edge : outgoing) {
            if (edge.isDefaultFlow()) {
                defaultFlow = edge;
                continue;
            }
            if (!edge.hasCondition()) {
                // Unconditional non-default flow on an XOR split — engine takes it
                return List.of(edge);
            }
            switch (evaluateAndRecord(gateway, edge, variables, result)) {
                case TRUE -> {
                    return List.of(edge);
                }
                case UNEVALUATED -> {
                    if (firstUnevaluated == null) {
                        firstUnevaluated = edge;
                    }
                }
                case FALSE -> { /* keep looking */ }
            }
        }

        if (firstUnevaluated != null) {
            result.getNotes().add("Gateway '" + gateway.getName()
                    + "': could not evaluate condition '" + firstUnevaluated.getCondition()
                    + "'" + (defaultFlow != null ? " — taking the declared default flow"
                    : " — traversal of this path ends here"));
        }
        if (defaultFlow != null) {
            return List.of(defaultFlow);
        }
        if (firstUnevaluated != null) {
            return List.of();
        }
        result.getNotes().add("Gateway '" + gateway.getName()
                + "': no condition matched and no default flow declared — path ends here");
        return List.of();
    }

    private ConditionEvaluator.Outcome evaluateAndRecord(WorkflowNode node, WorkflowEdge edge,
                                                         Map<String, Object> variables,
                                                         SimulationResult result) {
        ConditionEvaluator.Outcome outcome =
                ConditionEvaluator.evaluate(edge.getCondition(), variables);
        GatewayDecision decision = new GatewayDecision(
                node.getId(), node.getName(), edge.getCondition(), outcome == ConditionEvaluator.Outcome.TRUE);
        decision.setEvaluation(outcome.name());
        decision.setSelectedPath(outcome == ConditionEvaluator.Outcome.TRUE ? edge.getTarget() : null);
        result.getGatewayDecisions().put(node.getId() + "->" + edge.getTarget(), decision);
        return outcome;
    }

    /**
     * Honest what-if: runs the original inputs and (when provided) a second
     * run with modifiedVariables so branch outcomes can be compared. The
     * changeDescription is reported as context, never as a fake mutation.
     */
    public WhatIfResult whatIf(WorkflowDefinition workflow, Map<String, Object> inputVariables,
                               Map<String, Object> modifiedVariables, String changeDescription) {
        log.info("What-if analysis for workflow: {}", workflow.getName());

        WhatIfResult result = new WhatIfResult();
        result.setChangeDescription(changeDescription);
        result.setOriginalResult(simulate(workflow, inputVariables));

        if (modifiedVariables != null && !modifiedVariables.isEmpty()) {
            Map<String, Object> merged = new HashMap<>(
                    inputVariables != null ? inputVariables : Map.of());
            merged.putAll(modifiedVariables);
            result.setModifiedResult(simulate(workflow, merged));
            result.setAnalysis("Compared the walkthrough with your variables versus the "
                    + "modified variable set — paths that differ reflect only conditions "
                    + "the simulator can actually evaluate.");
        } else {
            result.setAnalysis("No modifiedVariables supplied, so no second run was performed. "
                    + "Provide alternative input variables to compare branch outcomes.");
        }
        return result;
    }
}
