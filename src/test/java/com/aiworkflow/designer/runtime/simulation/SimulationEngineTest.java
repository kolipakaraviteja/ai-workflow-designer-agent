package com.aiworkflow.designer.runtime.simulation;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationEngineTest {

    private final SimulationEngine engine = new SimulationEngine();

    private WorkflowDefinition base() {
        WorkflowDefinition w = new WorkflowDefinition("Sim", "test");
        w.addNode(new WorkflowNode("start", "START_EVENT", "Start"));
        w.addNode(new WorkflowNode("t1", "USER_TASK", "Task"));
        w.addNode(new WorkflowNode("end", "END_EVENT", "End"));
        w.addEdge(new WorkflowEdge("start", "t1"));
        w.addEdge(new WorkflowEdge("t1", "end"));
        return w;
    }

    @Test
    void walksLinearPathToEnd() {
        SimulationResult result = engine.simulate(base(), Map.of());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutionPath()).containsExactly("start", "t1", "end");
        assertThat(result.getOutputVariables()).containsEntry("processCompleted", true);
    }

    @Test
    void exclusiveGatewayFollowsEvaluatedBranch() {
        WorkflowDefinition w = base();
        w.addNode(new WorkflowNode("gw", "EXCLUSIVE_GATEWAY", "Decision"));
        w.addNode(new WorkflowNode("approved_t", "USER_TASK", "Approved Path"));
        w.addNode(new WorkflowNode("rejected_t", "USER_TASK", "Rejected Path"));
        // rewire: t1 -> gw -> {approved_t|rejected_t} -> end
        w.getEdges().removeIf(e -> e.getTarget().equals("end"));
        w.addEdge(new WorkflowEdge("t1", "gw"));
        w.addEdge(new WorkflowEdge("gw", "approved_t", "${approved == true}"));
        w.addEdge(new WorkflowEdge("gw", "rejected_t", "${approved == false}"));
        w.addEdge(new WorkflowEdge("approved_t", "end"));
        w.addEdge(new WorkflowEdge("rejected_t", "end"));

        SimulationResult approved = engine.simulate(w, Map.of("approved", true));
        assertThat(approved.getExecutionPath()).contains("approved_t");
        assertThat(approved.getExecutionPath()).doesNotContain("rejected_t");

        SimulationResult rejected = engine.simulate(w, Map.of("approved", false));
        assertThat(rejected.getExecutionPath()).contains("rejected_t");
        assertThat(rejected.getExecutionPath()).doesNotContain("approved_t");
    }

    @Test
    void unevaluatedConditionTakesDefaultFlowAndSaysSo() {
        WorkflowDefinition w = base();
        w.addNode(new WorkflowNode("gw", "EXCLUSIVE_GATEWAY", "Decision"));
        w.addNode(new WorkflowNode("complex_t", "USER_TASK", "Complex Path"));
        w.addNode(new WorkflowNode("default_t", "USER_TASK", "Default Path"));
        w.getEdges().removeIf(e -> e.getTarget().equals("end"));
        w.addEdge(new WorkflowEdge("t1", "gw"));
        w.addEdge(new WorkflowEdge("gw", "complex_t", "${bean.method()}"));
        WorkflowEdge defaultFlow = new WorkflowEdge("gw", "default_t");
        defaultFlow.setType("DEFAULT_FLOW");
        w.addEdge(defaultFlow);
        w.addEdge(new WorkflowEdge("complex_t", "end"));
        w.addEdge(new WorkflowEdge("default_t", "end"));

        SimulationResult result = engine.simulate(w, Map.of());
        assertThat(result.getExecutionPath()).contains("default_t");
        assertThat(result.getExecutionPath()).doesNotContain("complex_t");
        assertThat(result.getNotes()).anyMatch(n -> n.contains("could not evaluate"));
        assertThat(result.getGatewayDecisions().values())
                .anyMatch(d -> "UNEVALUATED".equals(d.getEvaluation()));
    }

    @Test
    void unevaluatedConditionWithoutDefaultEndsPathHonestly() {
        WorkflowDefinition w = base();
        w.addNode(new WorkflowNode("gw", "EXCLUSIVE_GATEWAY", "Decision"));
        w.addNode(new WorkflowNode("complex_t", "USER_TASK", "Complex Path"));
        w.getEdges().removeIf(e -> e.getTarget().equals("end"));
        w.addEdge(new WorkflowEdge("t1", "gw"));
        w.addEdge(new WorkflowEdge("gw", "complex_t", "${bean.method()}"));
        w.addEdge(new WorkflowEdge("complex_t", "end"));

        SimulationResult result = engine.simulate(w, Map.of());
        assertThat(result.getExecutionPath()).doesNotContain("complex_t", "end");
        assertThat(result.getNotes()).anyMatch(n -> n.contains("could not evaluate"));
    }

    @Test
    void reworkLoopIsBounded() {
        WorkflowDefinition w = base();
        w.addNode(new WorkflowNode("gw", "EXCLUSIVE_GATEWAY", "Loop"));
        w.getEdges().removeIf(e -> e.getTarget().equals("end"));
        w.addEdge(new WorkflowEdge("t1", "gw"));
        // Always-true loop back + exit
        w.addEdge(new WorkflowEdge("gw", "t1", "${again == true}"));
        w.addEdge(new WorkflowEdge("gw", "end", "${again == false}"));

        SimulationResult result = engine.simulate(w, Map.of("again", true));
        // Must terminate (node-visit cap) rather than hang
        assertThat(result.getExecutionPath()).isNotEmpty();
        assertThat(result.getNotes()).anyMatch(n -> n.contains("loop") || n.contains("visited"));
    }

    @Test
    void whatIfWithModifiedVariablesRunsTwice() {
        WorkflowDefinition w = base();
        w.addNode(new WorkflowNode("gw", "EXCLUSIVE_GATEWAY", "D"));
        w.addNode(new WorkflowNode("yes_t", "USER_TASK", "Yes"));
        w.addNode(new WorkflowNode("no_t", "USER_TASK", "No"));
        w.getEdges().removeIf(e -> e.getTarget().equals("end"));
        w.addEdge(new WorkflowEdge("t1", "gw"));
        w.addEdge(new WorkflowEdge("gw", "yes_t", "${flag == true}"));
        w.addEdge(new WorkflowEdge("gw", "no_t", "${flag == false}"));
        w.addEdge(new WorkflowEdge("yes_t", "end"));
        w.addEdge(new WorkflowEdge("no_t", "end"));

        WhatIfResult result = engine.whatIf(w, Map.of("flag", true),
                Map.of("flag", false), "flip flag");
        assertThat(result.getOriginalResult().getExecutionPath()).contains("yes_t");
        assertThat(result.getModifiedResult().getExecutionPath()).contains("no_t");
    }
}
