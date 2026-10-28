package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphToolsTest {

    private WorkflowDefinition workflow;

    @BeforeEach
    void setUp() {
        workflow = new WorkflowDefinition("Test", "desc");
        workflow.addNode(new WorkflowNode("start", "START_EVENT", "Start"));
        workflow.addNode(new WorkflowNode("t1", "USER_TASK", "Task 1"));
        workflow.addNode(new WorkflowNode("end", "END_EVENT", "End"));
        workflow.addEdge(new WorkflowEdge("start", "t1"));
        workflow.addEdge(new WorkflowEdge("t1", "end"));
    }

    @Test
    void addTaskRejectsDuplicateId() {
        AddTaskTool tool = new AddTaskTool();
        assertThatThrownBy(() -> tool.execute(workflow, Map.of(
                "taskId", "t1", "taskType", "USER_TASK", "taskName", "Dupe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addTaskRejectsInvalidType() {
        AddTaskTool tool = new AddTaskTool();
        assertThatThrownBy(() -> tool.execute(workflow, Map.of(
                "taskType", "MADE_UP_TASK", "taskName", "Bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported task type");
    }

    @Test
    void addTaskStoresOptionalProperties() {
        AddTaskTool tool = new AddTaskTool();
        tool.execute(workflow, Map.of(
                "taskId", "svc1", "taskType", "SERVICE_TASK", "taskName", "Svc",
                "expression", "${leaveService.check(execution)}"));
        assertThat(workflow.getNodeById("svc1").getProperty("expression"))
                .isEqualTo("${leaveService.check(execution)}");

        tool.execute(workflow, Map.of(
                "taskId", "ut1", "taskType", "USER_TASK", "taskName", "Human",
                "candidateGroups", "managers"));
        assertThat(workflow.getNodeById("ut1").getProperty("candidateGroups"))
                .isEqualTo("managers");
    }

    @Test
    void connectNodesRejectsDuplicateEdge() {
        ConnectNodesTool tool = new ConnectNodesTool();
        assertThatThrownBy(() -> tool.execute(workflow, Map.of(
                "sourceId", "start", "targetId", "t1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void connectNodesRejectsMissingEndpoints() {
        ConnectNodesTool tool = new ConnectNodesTool();
        assertThatThrownBy(() -> tool.execute(workflow, Map.of(
                "sourceId", "start", "targetId", "ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target node not found");
    }

    @Test
    void updateTaskChangesTypeAndClearsStaleProperties() {
        UpdateTaskTool tool = new UpdateTaskTool();
        workflow.getNodeById("t1").setProperty("assignee", "manager");

        tool.execute(workflow, Map.of(
                "taskId", "t1", "taskType", "SERVICE_TASK",
                "serviceClass", "com.example.NotifyDelegate"));

        WorkflowNode node = workflow.getNodeById("t1");
        assertThat(node.getType()).isEqualTo("SERVICE_TASK");
        assertThat(node.getProperty("serviceClass")).isEqualTo("com.example.NotifyDelegate");
        assertThat(node.getProperty("assignee")).isNull();
        // Edges must be preserved
        assertThat(workflow.getEdgesToNode("t1")).hasSize(1);
        assertThat(workflow.getEdgesFromNode("t1")).hasSize(1);
    }

    @Test
    void updateTaskRefusesNonTaskNodes() {
        UpdateTaskTool tool = new UpdateTaskTool();
        assertThatThrownBy(() -> tool.execute(workflow, Map.of("taskId", "start", "taskName", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a task");
    }

    @Test
    void removeTaskReconnectsAndPreservesIncomingCondition() {
        // start -(cond)-> t1 -> end : removing t1 must keep the condition
        WorkflowEdge in = workflow.getEdgesToNode("t1").get(0);
        in.setCondition("${approved == true}");

        new RemoveTaskTool().execute(workflow, Map.of("taskId", "t1"));

        assertThat(workflow.getNodeById("t1")).isNull();
        assertThat(workflow.getEdges()).hasSize(1);
        WorkflowEdge reconnected = workflow.getEdges().get(0);
        assertThat(reconnected.getSource()).isEqualTo("start");
        assertThat(reconnected.getTarget()).isEqualTo("end");
        assertThat(reconnected.getCondition()).isEqualTo("${approved == true}");
    }

    @Test
    void removeTaskRefusesBranchNodes() {
        workflow.addNode(new WorkflowNode("end2", "END_EVENT", "Alt End"));
        workflow.addEdge(new WorkflowEdge("t1", "end2"));

        assertThatThrownBy(() -> new RemoveTaskTool().execute(workflow, Map.of("taskId", "t1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refusing to delete");
        // Graph must be untouched
        assertThat(workflow.getNodeById("t1")).isNotNull();
        assertThat(workflow.getEdges()).hasSize(3);
    }

    @Test
    void removeTaskRefusesNonTaskNodes() {
        assertThatThrownBy(() -> new RemoveTaskTool().execute(workflow, Map.of("taskId", "start")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a task");
    }

    @Test
    void addConditionUpdatesExistingEdgeInPlace() {
        new AddConditionTool().execute(workflow, Map.of(
                "sourceId", "start", "targetId", "t1", "condition", "${x > 3}"));

        assertThat(workflow.getEdges()).hasSize(2); // no new edge created
        assertThat(workflow.getEdgesToNode("t1").get(0).getCondition()).isEqualTo("${x > 3}");
    }

    @Test
    void addConditionFailsForMissingEdge() {
        assertThatThrownBy(() -> new AddConditionTool().execute(workflow, Map.of(
                "sourceId", "start", "targetId", "end", "condition", "${x}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existing sequence flow");
    }

    @Test
    void createWorkflowRefusesToReplaceNonEmptyGraph() {
        assertThatThrownBy(() -> new CreateWorkflowTool().execute(workflow, Map.of(
                "name", "Fresh", "description", "new")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        Object result = new CreateWorkflowTool().execute(workflow, Map.of(
                "name", "Fresh", "description", "new", "forceReplace", true));
        assertThat(result).isInstanceOf(WorkflowDefinition.class);
    }

    @Test
    void invariantsDetectCorruption() {
        workflow.addNode(new WorkflowNode("t1", "USER_TASK", "Dup id"));
        workflow.addEdge(new WorkflowEdge("start", "t1"));
        workflow.addEdge(new WorkflowEdge("start", "t1"));
        workflow.addEdge(new WorkflowEdge("ghost", "end"));

        var violations = GraphInvariants.check(workflow);
        assertThat(violations).anyMatch(v -> v.contains("Duplicate node id"));
        assertThat(violations).anyMatch(v -> v.contains("Duplicate sequence flow"));
        assertThat(violations).anyMatch(v -> v.contains("missing source"));
    }
}
