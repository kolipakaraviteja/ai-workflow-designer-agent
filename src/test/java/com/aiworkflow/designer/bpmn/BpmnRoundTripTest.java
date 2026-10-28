package com.aiworkflow.designer.bpmn;

import com.aiworkflow.designer.runtime.flowable.FlowableDeploymentService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Semantic round-trip tests: definition -> BPMN XML -> Flowable-validated ->
 * parsed definition. Node/edge counts alone don't count — types, properties
 * and conditions must survive the round trip.
 */
class BpmnRoundTripTest {

    private final BpmnGenerator generator = new BpmnGenerator();
    private final BpmnParser parser = new BpmnParser();
    private final FlowableDeploymentService deploymentService =
            new FlowableDeploymentService(null, null, null);

    private WorkflowDefinition workflow;

    @BeforeEach
    void setUp() {
        workflow = new WorkflowDefinition("Leave Request", "Employee leave approval");
        workflow.setId("leave_process");

        workflow.addNode(new WorkflowNode("start_evt", "START_EVENT", "Start"));

        WorkflowNode submit = new WorkflowNode("submit_req", "USER_TASK", "Submit Request");
        submit.setProperty("assignee", "employee");
        workflow.addNode(submit);

        WorkflowNode check = new WorkflowNode("check_balance", "SERVICE_TASK", "Check Balance");
        check.setProperty("serviceClass", "com.example.LeaveBalanceService");
        workflow.addNode(check);

        workflow.addNode(new WorkflowNode("gw_decision", "EXCLUSIVE_GATEWAY", "Enough days?"));

        WorkflowNode approve = new WorkflowNode("mgr_approval", "USER_TASK", "Manager Approval");
        approve.setProperty("assignee", "manager");
        workflow.addNode(approve);

        WorkflowNode reject = new WorkflowNode("send_rejection", "SERVICE_TASK", "Send Rejection");
        reject.setProperty("expression", "${notifier.reject(request)}");
        workflow.addNode(reject);

        WorkflowNode timer = new WorkflowNode("wait_timer", "TIMER_EVENT", "Wait 3 days");
        timer.setProperty("timerExpression", "P3D");
        workflow.addNode(timer);

        workflow.addNode(new WorkflowNode("end_evt", "END_EVENT", "Done"));

        workflow.addEdge(new WorkflowEdge("start_evt", "submit_req"));
        workflow.addEdge(new WorkflowEdge("submit_req", "check_balance"));
        workflow.addEdge(new WorkflowEdge("check_balance", "gw_decision"));
        workflow.addEdge(new WorkflowEdge("gw_decision", "mgr_approval", "${balance >= days}"));
        workflow.addEdge(new WorkflowEdge("gw_decision", "send_rejection", "${balance < days}"));
        workflow.addEdge(new WorkflowEdge("mgr_approval", "wait_timer"));
        workflow.addEdge(new WorkflowEdge("send_rejection", "end_evt"));
        workflow.addEdge(new WorkflowEdge("wait_timer", "end_evt"));
    }

    @Test
    void generatedXmlIsFlowableDeployable() {
        String xml = generator.generateBpmnXml(workflow);
        // Throws on malformed XML or Flowable validation errors
        deploymentService.verifyDeployable(xml);
    }

    @Test
    void roundTripPreservesNodeSemantics() {
        String xml = generator.generateBpmnXml(workflow);
        WorkflowDefinition parsed = parser.parseBpmnXml(xml);

        assertThat(parsed.getNodes()).hasSameSizeAs(workflow.getNodes());
        assertThat(parsed.getEdges()).hasSameSizeAs(workflow.getEdges());

        WorkflowNode mgrTask = parsed.getNodeById("mgr_approval");
        assertThat(mgrTask.getType()).isEqualTo("USER_TASK");
        assertThat(mgrTask.getProperty("assignee")).isEqualTo("manager");

        WorkflowNode svc = parsed.getNodeById("check_balance");
        assertThat(svc.getType()).isEqualTo("SERVICE_TASK");
        assertThat(svc.getProperty("serviceClass")).isEqualTo("com.example.LeaveBalanceService");

        WorkflowNode rejection = parsed.getNodeById("send_rejection");
        assertThat(rejection.getProperty("expression")).isEqualTo("${notifier.reject(request)}");

        WorkflowNode timer = parsed.getNodeById("wait_timer");
        assertThat(timer.getType()).isEqualTo("TIMER_EVENT");
        assertThat(timer.getProperty("timerExpression")).isEqualTo("P3D");

        assertThat(parsed.getNodeById("gw_decision").getType()).isEqualTo("EXCLUSIVE_GATEWAY");
    }

    @Test
    void roundTripPreservesEdgeConditions() {
        String xml = generator.generateBpmnXml(workflow);
        WorkflowDefinition parsed = parser.parseBpmnXml(xml);

        WorkflowEdge approveFlow = parsed.getEdges().stream()
                .filter(e -> e.getSource().equals("gw_decision") && e.getTarget().equals("mgr_approval"))
                .findFirst().orElseThrow();
        assertThat(approveFlow.getCondition()).contains("balance >= days");

        WorkflowEdge rejectFlow = parsed.getEdges().stream()
                .filter(e -> e.getSource().equals("gw_decision") && e.getTarget().equals("send_rejection"))
                .findFirst().orElseThrow();
        assertThat(rejectFlow.getCondition()).contains("balance < days");
    }

    @Test
    void generatedXmlContainsCorrectElements() {
        String xml = generator.generateBpmnXml(workflow);
        assertThat(xml).contains("<userTask");
        assertThat(xml).contains("<serviceTask");
        assertThat(xml).contains("<exclusiveGateway");
        assertThat(xml).contains("<intermediateCatchEvent");
        assertThat(xml).contains("timerEventDefinition");
        assertThat(xml).contains("conditionExpression");
        assertThat(xml).contains("BPMNDiagram");
    }

    @Test
    void parserRejectsDoctypeAttacks() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE definitions [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="p" name="&xxe;"><startEvent id="s"/></process>
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parseBpmnXml(xxe))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parserRejectsUnsupportedElements() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             id="Defs" targetNamespace="http://bpmn.io/schema/bpmn">
                  <process id="p" name="Sub">
                    <startEvent id="s"/>
                    <subProcess id="sub"><task id="inner"/></subProcess>
                    <endEvent id="e"/>
                    <sequenceFlow id="f1" sourceRef="s" targetRef="sub"/>
                    <sequenceFlow id="f2" sourceRef="sub" targetRef="e"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parseBpmnXml(xml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sub-process");
    }

    @Test
    void cyclicWorkflowLayoutTerminates() {
        // Rework loop: gw -> rework -> gw must not hang layout/generation
        workflow.addNode(new WorkflowNode("rework", "USER_TASK", "Rework"));
        workflow.addEdge(new WorkflowEdge("gw_decision", "rework", "${needs_rework == true}"));
        workflow.addEdge(new WorkflowEdge("rework", "gw_decision"));

        String xml = generator.generateBpmnXml(workflow);
        assertThat(xml).isNotBlank();
    }
}
