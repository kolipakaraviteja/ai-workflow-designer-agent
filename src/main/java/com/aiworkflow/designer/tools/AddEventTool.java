package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AddEventTool implements WorkflowTool {

    @Override
    public String getName() {
        return "add_event";
    }

    @Override
    public String getDescription() {
        return "Add a BPMN event to the workflow (START_EVENT, END_EVENT, TIMER_EVENT, MESSAGE_EVENT, ERROR_EVENT, SIGNAL_EVENT)";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing add_event tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String eventId = (String) parameters.get("eventId");
        String eventType = (String) parameters.get("eventType");
        String eventName = (String) parameters.get("eventName");
        String eventDefinition = (String) parameters.get("eventDefinition");

        if (eventType == null || eventType.trim().isEmpty()) {
            eventType = "START_EVENT";
        }
        eventType = GraphInvariants.requireValidType("event", eventType);

        if (eventId == null || eventId.trim().isEmpty()) {
            eventId = "event_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        GraphInvariants.requireUniqueNodeId(workflow, eventId);

        if (eventName == null || eventName.trim().isEmpty()) {
            eventName = "New Event";
        }

        WorkflowNode event = new WorkflowNode(eventId, eventType, eventName);

        if (eventDefinition != null && !eventDefinition.trim().isEmpty()) {
            event.setProperty("eventDefinition", eventDefinition);
        }

        workflow.addNode(event);

        log.info("Added event {} to workflow", eventName);
        return Map.of(
                "success", true,
                "eventId", eventId,
                "eventName", eventName,
                "eventType", eventType
        );
    }
}
