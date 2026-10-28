package com.aiworkflow.designer.bpmn;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BusinessRuleTask;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ComplexGateway;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ErrorEventDefinition;
import org.flowable.bpmn.model.EventGateway;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.ManualTask;
import org.flowable.bpmn.model.MessageEventDefinition;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ReceiveTask;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.bpmn.model.SendTask;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SignalEventDefinition;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses BPMN 2.0 XML with Flowable's {@link BpmnXMLConverter} and maps the
 * elements the internal model supports. Element ids are preserved verbatim so
 * import/export round-trips are stable. Elements the internal model cannot
 * represent (subprocesses, call activities, boundary events, pools/lanes...)
 * are rejected explicitly instead of being silently dropped.
 */
@Slf4j
@Component
public class BpmnParser {

    public WorkflowDefinition parseBpmnXml(String bpmnXml) {
        log.info("Parsing BPMN XML");

        BpmnModel model;
        try {
            XMLStreamReader reader = secureXmlStreamReader(bpmnXml);
            model = new BpmnXMLConverter().convertToBpmnModel(reader);
        } catch (Exception e) {
            throw new IllegalArgumentException("BPMN XML is not parseable: " + e.getMessage(), e);
        }

        Process process = model.getMainProcess();
        if (process == null) {
            throw new IllegalArgumentException("No process element found in BPMN XML");
        }
        rejectUnsupported(model, process);

        WorkflowDefinition workflow = new WorkflowDefinition(
                process.getName() != null && !process.getName().isBlank()
                        ? process.getName() : "Imported Workflow",
                "Imported from BPMN XML");
        workflow.setId(process.getId());

        // Gateway default flows must be known before edges are mapped
        Map<String, String> defaultFlowByGateway = new HashMap<>();
        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof Gateway gateway && gateway.getDefaultFlow() != null) {
                defaultFlowByGateway.put(element.getId(), gateway.getDefaultFlow());
            }
        }

        for (FlowElement element : process.getFlowElements()) {
            WorkflowNode node = toNode(element);
            if (node != null) {
                workflow.addNode(node);
            }
        }

        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof SequenceFlow flow) {
                WorkflowEdge edge = toEdge(flow, defaultFlowByGateway);
                if (edge != null) {
                    workflow.addEdge(edge);
                }
            }
        }

        log.info("Parsed BPMN workflow with {} nodes and {} edges",
                workflow.getNodes().size(), workflow.getEdges().size());
        return workflow;
    }

    /** StAX reader hardened against XXE: no DTDs, no external entities. */
    private XMLStreamReader secureXmlStreamReader(String xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Fails fast on constructs the internal model cannot represent, so import
     * never silently produces a different process than the XML describes.
     */
    private void rejectUnsupported(BpmnModel model, Process process) {
        if (!model.getPools().isEmpty()) {
            throw new IllegalArgumentException(
                    "BPMN pools/participants are not supported — import a single-process diagram");
        }
        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof SubProcess) {
                throw new IllegalArgumentException(
                        "Sub-process '" + element.getId() + "' is not supported");
            }
            if (element instanceof CallActivity) {
                throw new IllegalArgumentException(
                        "Call activity '" + element.getId() + "' is not supported");
            }
            if (element instanceof BoundaryEvent) {
                throw new IllegalArgumentException(
                        "Boundary event '" + element.getId() + "' is not supported");
            }
        }
        if (!process.getLanes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Lanes are not supported — import a process without lanes");
        }
    }

    /** Returns null for sequence flows (handled separately) and non-flow elements. */
    private WorkflowNode toNode(FlowElement element) {
        WorkflowNode node;
        switch (element) {
            case SequenceFlow ignored -> { return null; }
            case StartEvent ignored1 -> node = new WorkflowNode(element.getId(), "START_EVENT", name(element, "Start"));
            case EndEvent end -> {
                node = new WorkflowNode(element.getId(), "END_EVENT", name(element, "End"));
                if (!end.getEventDefinitions().isEmpty()
                        && end.getEventDefinitions().get(0) instanceof ErrorEventDefinition def) {
                    node.setProperty("errorCode", def.getErrorCode());
                }
            }
            case UserTask task -> {
                node = new WorkflowNode(element.getId(), "USER_TASK", name(element, "User Task"));
                putIfNotBlank(node, "assignee", task.getAssignee());
                putIfNotBlank(node, "candidateUsers", String.join(",", task.getCandidateUsers()));
                putIfNotBlank(node, "candidateGroups", String.join(",", task.getCandidateGroups()));
                putIfNotBlank(node, "documentation", task.getDocumentation());
            }
            case ServiceTask task -> {
                node = new WorkflowNode(element.getId(), "SERVICE_TASK", name(element, "Service Task"));
                String type = task.getImplementationType();
                if (ImplementationType.IMPLEMENTATION_TYPE_CLASS.equals(type)) {
                    putIfNotBlank(node, "serviceClass", task.getImplementation());
                } else if (ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(type)) {
                    putIfNotBlank(node, "delegateExpression", task.getImplementation());
                } else {
                    putIfNotBlank(node, "expression", task.getImplementation());
                }
            }
            case ScriptTask task -> {
                node = new WorkflowNode(element.getId(), "SCRIPT_TASK", name(element, "Script Task"));
                putIfNotBlank(node, "scriptFormat", task.getScriptFormat());
                putIfNotBlank(node, "script", task.getScript());
            }
            case BusinessRuleTask ignored2 -> node = new WorkflowNode(element.getId(), "BUSINESS_RULE_TASK", name(element, "Business Rule Task"));
            case SendTask ignored3 -> node = new WorkflowNode(element.getId(), "SEND_TASK", name(element, "Send Task"));
            case ReceiveTask ignored4 -> node = new WorkflowNode(element.getId(), "RECEIVE_TASK", name(element, "Receive Task"));
            case ManualTask ignored5 -> node = new WorkflowNode(element.getId(), "MANUAL_TASK", name(element, "Manual Task"));
            case ExclusiveGateway ignored6 -> node = new WorkflowNode(element.getId(), "EXCLUSIVE_GATEWAY", name(element, "Exclusive Gateway"));
            case ParallelGateway ignored7 -> node = new WorkflowNode(element.getId(), "PARALLEL_GATEWAY", name(element, "Parallel Gateway"));
            case InclusiveGateway ignored8 -> node = new WorkflowNode(element.getId(), "INCLUSIVE_GATEWAY", name(element, "Inclusive Gateway"));
            case EventGateway ignored9 -> node = new WorkflowNode(element.getId(), "EVENT_BASED_GATEWAY", name(element, "Event Gateway"));
            case ComplexGateway ignored10 -> node = new WorkflowNode(element.getId(), "COMPLEX_GATEWAY", name(element, "Complex Gateway"));
            case IntermediateCatchEvent event -> node = intermediateCatch(event);
            case ThrowEvent event -> node = throwEvent(event);
            default -> throw new IllegalArgumentException(
                    "Unsupported BPMN element '" + element.getId() + "' of type "
                            + element.getClass().getSimpleName());
        }
        return node;
    }

    private WorkflowNode intermediateCatch(IntermediateCatchEvent event) {
        String name = name(event, "Event");
        if (!event.getEventDefinitions().isEmpty()) {
            var def = event.getEventDefinitions().get(0);
            if (def instanceof TimerEventDefinition timer) {
                WorkflowNode node = new WorkflowNode(event.getId(), "TIMER_EVENT", name);
                putIfNotBlank(node, "timerExpression",
                        timer.getTimeDuration() != null ? timer.getTimeDuration()
                                : timer.getTimeDate() != null ? timer.getTimeDate()
                                : timer.getTimeCycle());
                return node;
            }
            if (def instanceof MessageEventDefinition message) {
                WorkflowNode node = new WorkflowNode(event.getId(), "MESSAGE_EVENT", name);
                putIfNotBlank(node, "messageRef", message.getMessageRef());
                return node;
            }
            if (def instanceof SignalEventDefinition signal) {
                WorkflowNode node = new WorkflowNode(event.getId(), "SIGNAL_EVENT", name);
                putIfNotBlank(node, "signalRef", signal.getSignalRef());
                return node;
            }
        }
        return new WorkflowNode(event.getId(), "INTERMEDIATE_EVENT", name);
    }

    private WorkflowNode throwEvent(ThrowEvent event) {
        if (!event.getEventDefinitions().isEmpty()
                && event.getEventDefinitions().get(0) instanceof ErrorEventDefinition def) {
            WorkflowNode node = new WorkflowNode(event.getId(), "ERROR_EVENT", name(event, "Error"));
            putIfNotBlank(node, "errorCode", def.getErrorCode());
            return node;
        }
        if (!event.getEventDefinitions().isEmpty()
                && event.getEventDefinitions().get(0) instanceof SignalEventDefinition def) {
            WorkflowNode node = new WorkflowNode(event.getId(), "SIGNAL_EVENT", name(event, "Signal"));
            putIfNotBlank(node, "signalRef", def.getSignalRef());
            return node;
        }
        return new WorkflowNode(event.getId(), "INTERMEDIATE_EVENT", name(event, "Event"));
    }

    private WorkflowEdge toEdge(SequenceFlow flow, Map<String, String> defaultFlowByGateway) {
        WorkflowEdge edge = new WorkflowEdge(flow.getSourceRef(), flow.getTargetRef());
        edge.setId(flow.getId() != null ? flow.getId() : edge.getId());
        if (flow.getConditionExpression() != null && !flow.getConditionExpression().isBlank()) {
            edge.setCondition(flow.getConditionExpression());
        } else if (flow.getName() != null && !flow.getName().isBlank()) {
            edge.setCondition(flow.getName());
        }
        if (flow.getId() != null && flow.getId().equals(defaultFlowByGateway.get(flow.getSourceRef()))) {
            edge.setType("DEFAULT_FLOW");
        }
        return edge;
    }

    private String name(FlowElement element, String fallback) {
        return element.getName() != null && !element.getName().isBlank()
                ? element.getName() : fallback;
    }

    private void putIfNotBlank(WorkflowNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.setProperty(key, value);
        }
    }
}
