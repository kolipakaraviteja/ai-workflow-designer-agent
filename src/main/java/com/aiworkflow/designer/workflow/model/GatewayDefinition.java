package com.aiworkflow.designer.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GatewayDefinition {

    @Builder.Default
    private String gatewayType = "EXCLUSIVE"; // EXCLUSIVE, PARALLEL, INCLUSIVE, EVENT_BASED
    
    @Builder.Default
    private String direction = "DIVERGING"; // DIVERGING, CONVERGING
    
    @Builder.Default
    private List<GatewayCondition> conditions = new java.util.ArrayList<>();
    
    private String defaultFlowId;
    
    @Builder.Default
    private Boolean async = false;
    
    @Builder.Default
    private Boolean exclusive = true;
    
    @Builder.Default
    private Map<String, Object> properties = new java.util.HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayCondition {
        private String flowId;
        private String conditionExpression;
        private String label;
        private Boolean isDefault;
    }
}
