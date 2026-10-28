package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.aiworkflow.designer.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AddGatewayTool implements WorkflowTool {

    @Override
    public String getName() {
        return "add_gateway";
    }

    @Override
    public String getDescription() {
        return "Add a gateway to the workflow (EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY)";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing add_gateway tool with parameters: {}", parameters);

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String gatewayId = (String) parameters.get("gatewayId");
        String gatewayType = (String) parameters.get("gatewayType");
        String gatewayName = (String) parameters.get("gatewayName");

        if (gatewayType == null || gatewayType.trim().isEmpty()) {
            gatewayType = "EXCLUSIVE_GATEWAY";
        }
        gatewayType = GraphInvariants.requireValidType("gateway", gatewayType);

        if (gatewayId == null || gatewayId.trim().isEmpty()) {
            gatewayId = "gateway_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        GraphInvariants.requireUniqueNodeId(workflow, gatewayId);

        if (gatewayName == null || gatewayName.trim().isEmpty()) {
            gatewayName = "Gateway";
        }

        WorkflowNode gateway = new WorkflowNode(gatewayId, gatewayType, gatewayName);
        workflow.addNode(gateway);

        log.info("Added gateway {} to workflow", gatewayName);
        return Map.of(
                "success", true,
                "gatewayId", gatewayId,
                "gatewayName", gatewayName,
                "gatewayType", gatewayType
        );
    }
}
