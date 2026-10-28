package com.aiworkflow.designer.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
public class EventDefinition {

    private String eventType; // TIMER, MESSAGE, ERROR, SIGNAL, COMPENSATION, CONDITIONAL, ESCALATION
    private String eventSubType; // START, END, INTERMEDIATE, BOUNDARY
    private String messageRef;
    private String signalRef;
    private String errorRef;
    private String escalationRef;
    private String timerExpression; // ISO 8601 duration, date, or cycle
    private String conditionExpression;
    
    @Builder.Default
    private Boolean interrupting = true; // For boundary events
    
    @Builder.Default
    private Boolean parallelMultiple = false; // For multi-instance events
    
    @Builder.Default
    private Map<String, Object> properties = new java.util.HashMap<>();

    public boolean isTimerEvent() {
        return "TIMER".equals(eventType);
    }

    public boolean isMessageEvent() {
        return "MESSAGE".equals(eventType);
    }

    public boolean isErrorEvent() {
        return "ERROR".equals(eventType);
    }

    public boolean isSignalEvent() {
        return "SIGNAL".equals(eventType);
    }

    public boolean isBoundaryEvent() {
        return "BOUNDARY".equals(eventSubType);
    }

    public boolean isInterrupting() {
        return interrupting != null && interrupting;
    }
}
