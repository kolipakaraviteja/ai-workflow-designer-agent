package com.aiworkflow.designer.runtime.simulation;

public class GatewayDecision {

    private String gatewayId;
    private String gatewayName;
    private String condition;
    private boolean conditionMet;
    /** TRUE, FALSE, or UNEVALUATED — honest record of what evaluation produced. */
    private String evaluation;
    private String selectedPath;

    public GatewayDecision() {
    }

    public GatewayDecision(String gatewayId, String gatewayName, String condition, boolean conditionMet) {
        this.gatewayId = gatewayId;
        this.gatewayName = gatewayName;
        this.condition = condition;
        this.conditionMet = conditionMet;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public boolean isConditionMet() {
        return conditionMet;
    }

    public void setConditionMet(boolean conditionMet) {
        this.conditionMet = conditionMet;
    }

    public String getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(String evaluation) {
        this.evaluation = evaluation;
    }

    public String getSelectedPath() {
        return selectedPath;
    }

    public void setSelectedPath(String selectedPath) {
        this.selectedPath = selectedPath;
    }
}
