package com.aiworkflow.designer.bpmn;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowEdge;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BusinessRuleTask;
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
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates BPMN 2.0 XML with Flowable's {@link BpmnModel} API — no string
 * concatenation, so namespaces, escaping, DI serialization and element syntax
 * are always correct. Layout is delegated to {@link BpmnAutoLayout}, which is
 * cycle-safe by construction.
 */
@Slf4j
@Component
public class BpmnGenerator {

    public String generateBpmnXml(WorkflowDefinition workflow) {
        log.info("Generating BPMN XML for workflow: {}", workflow.getName());

        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId(safeId(workflow.getId(), "Process"));
        process.setName(workflow.getName());
        model.addProcess(process);

        // Nodes first so flows can resolve endpoint references
        Map<String, String> bpmnIdByNodeId = new HashMap<>();
        Map<String, FlowElement> elements = new HashMap<>();
        for (WorkflowNode node : workflow.getNodes()) {
            String bpmnId = uniqueId(safeId(node.getId(), "Node"), elements.keySet());
            FlowElement element = toFlowElement(node, bpmnId);
            process.addFlowElement(element);
            elements.put(node.getId(), element);
            bpmnIdByNodeId.put(node.getId(), bpmnId);
        }

        for (WorkflowEdge edge : workflow.getEdges()) {
            String sourceRef = bpmnIdByNodeId.get(edge.getSource());
            String targetRef = bpmnIdByNodeId.get(edge.getTarget());
            if (sourceRef == null || targetRef == null) {
                throw new IllegalArgumentException(
                        "Cannot export flow " + edge.getId() + ": dangling endpoint ("
                                + edge.getSource() + " -> " + edge.getTarget() + ")");
            }

            SequenceFlow flow = new SequenceFlow(sourceRef, targetRef);
            flow.setId(safeId(edge.getId(), "flow"));
            if (edge.hasCondition()) {
                flow.setConditionExpression(normalizeCondition(edge.getCondition()));
                flow.setName(edge.getCondition());
            }
            process.addFlowElement(flow);

            FlowElement source = elements.get(edge.getSource());
            if (edge.isDefaultFlow() && source instanceof Gateway gateway) {
                gateway.setDefaultFlow(flow.getId());
            }
        }

        // Auto-layout produces DI shape/edge coordinates; safe on cyclic graphs
        new BpmnAutoLayout(model).execute();

        byte[] xml = new BpmnXMLConverter().convertToXML(model);
        String result = new String(xml, StandardCharsets.UTF_8);
        log.info("Generated BPMN XML with {} nodes and {} edges",
                workflow.getNodes().size(), workflow.getEdges().size());
        return result;
    }

    private FlowElement toFlowElement(WorkflowNode node, String bpmnId) {
        FlowElement element = switch (node.getType()) {
            case "START_EVENT" -> new StartEvent();
            case "END_EVENT" -> endEvent(node);
            case "USER_TASK" -> userTask(node);
            case "SERVICE_TASK" -> serviceTask(node);
            case "SCRIPT_TASK" -> scriptTask(node);
            case "BUSINESS_RULE_TASK" -> new BusinessRuleTask();
            case "SEND_TASK" -> new SendTask();
            case "RECEIVE_TASK" -> new ReceiveTask();
            case "MANUAL_TASK" -> new ManualTask();
            case "EXCLUSIVE_GATEWAY" -> new ExclusiveGateway();
            case "PARALLEL_GATEWAY" -> new ParallelGateway();
            case "INCLUSIVE_GATEWAY" -> new InclusiveGateway();
            case "EVENT_BASED_GATEWAY" -> new EventGateway();
            case "COMPLEX_GATEWAY" -> new ComplexGateway();
            case "TIMER_EVENT" -> timerCatchEvent(node);
            case "MESSAGE_EVENT" -> messageCatchEvent(node);
            case "ERROR_EVENT" -> errorThrowEvent(node);
            case "SIGNAL_EVENT" -> signalCatchEvent(node);
            case "INTERMEDIATE_EVENT" -> new IntermediateCatchEvent();
            default -> throw new IllegalArgumentException(
                    "Unsupported node type for BPMN export: " + node.getType()
                            + " (node '" + node.getId() + "')");
        };
        element.setId(bpmnId);
        element.setName(node.getName());
        return element;
    }

    private EndEvent endEvent(WorkflowNode node) {
        EndEvent event = new EndEvent();
        String errorCode = stringProp(node, "errorCode");
        if (errorCode != null) {
            ErrorEventDefinition def = new ErrorEventDefinition();
            def.setErrorCode(errorCode);
            event.getEventDefinitions().add(def);
        }
        return event;
    }

    private UserTask userTask(WorkflowNode node) {
        UserTask task = new UserTask();
        task.setAssignee(stringProp(node, "assignee"));
        task.setCandidateUsers(csvProp(node, "candidateUsers"));
        task.setCandidateGroups(csvProp(node, "candidateGroups"));
        String documentation = stringProp(node, "documentation");
        if (documentation != null) {
            task.setDocumentation(documentation);
        }
        return task;
    }

    private ServiceTask serviceTask(WorkflowNode node) {
        ServiceTask task = new ServiceTask();
        String serviceClass = stringProp(node, "serviceClass");
        String expression = stringProp(node, "expression");
        String delegateExpression = stringProp(node, "delegateExpression");
        if (serviceClass != null) {
            task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
            task.setImplementation(serviceClass);
        } else if (expression != null) {
            task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION);
            task.setImplementation(expression);
        } else if (delegateExpression != null) {
            task.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
            task.setImplementation(delegateExpression);
        }
        return task;
    }

    private ScriptTask scriptTask(WorkflowNode node) {
        ScriptTask task = new ScriptTask();
        task.setScriptFormat(stringProp(node, "scriptFormat"));
        task.setScript(stringProp(node, "script"));
        return task;
    }

    private IntermediateCatchEvent timerCatchEvent(WorkflowNode node) {
        IntermediateCatchEvent event = new IntermediateCatchEvent();
        TimerEventDefinition def = new TimerEventDefinition();
        String expression = firstProp(node, "timerExpression", "timeDuration", "eventDefinition");
        if (expression != null) {
            def.setTimeDuration(expression);
        }
        event.getEventDefinitions().add(def);
        return event;
    }

    private IntermediateCatchEvent messageCatchEvent(WorkflowNode node) {
        IntermediateCatchEvent event = new IntermediateCatchEvent();
        MessageEventDefinition def = new MessageEventDefinition();
        String ref = firstProp(node, "messageRef", "eventDefinition");
        if (ref != null) {
            def.setMessageRef(ref);
        }
        event.getEventDefinitions().add(def);
        return event;
    }

    private ThrowEvent errorThrowEvent(WorkflowNode node) {
        ThrowEvent event = new ThrowEvent();
        ErrorEventDefinition def = new ErrorEventDefinition();
        String code = firstProp(node, "errorCode", "eventDefinition");
        if (code != null) {
            def.setErrorCode(code);
        }
        event.getEventDefinitions().add(def);
        return event;
    }

    private IntermediateCatchEvent signalCatchEvent(WorkflowNode node) {
        IntermediateCatchEvent event = new IntermediateCatchEvent();
        SignalEventDefinition def = new SignalEventDefinition();
        String ref = firstProp(node, "signalRef", "eventDefinition");
        if (ref != null) {
            def.setSignalRef(ref);
        }
        event.getEventDefinitions().add(def);
        return event;
    }

    /**
     * Engine-side conditions must be UEL expressions — wrap bare text so a
     * condition like "approved == true" deploys instead of being evaluated as
     * a variable name.
     */
    static String normalizeCondition(String condition) {
        String c = condition.trim();
        return c.startsWith("${") ? c : "${" + c + "}";
    }

    /**
     * BPMN ids must be NCNames: start with a letter/underscore, then letters,
     * digits, '.', '-' or '_'. Anything else becomes '_'.
     */
    static String safeId(String raw, String fallbackPrefix) {
        if (raw == null || raw.isBlank()) {
            return fallbackPrefix + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private String uniqueId(String candidate, java.util.Collection<String> used) {
        String id = candidate;
        int suffix = 2;
        while (used.contains(id)) {
            id = candidate + "_" + suffix++;
        }
        return id;
    }

    private String stringProp(WorkflowNode node, String key) {
        Object value = node.getProperty(key);
        return value != null && !value.toString().isBlank() ? value.toString() : null;
    }

    private String firstProp(WorkflowNode node, String... keys) {
        for (String key : keys) {
            String value = stringProp(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> csvProp(WorkflowNode node, String key) {
        String value = stringProp(node, key);
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
