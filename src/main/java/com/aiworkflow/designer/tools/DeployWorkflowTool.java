package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.bpmn.BpmnGenerator;
import com.aiworkflow.designer.runtime.flowable.FlowableDeploymentService;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeployWorkflowTool implements WorkflowTool {

    private final BpmnGenerator bpmnGenerator;
    private final FlowableDeploymentService flowableDeploymentService;

    @Override
    public String getName() {
        return "deploy_workflow";
    }

    @Override
    public String getDescription() {
        return "Deploy the workflow to the Flowable BPMN engine for execution";
    }

    @Override
    public Object execute(WorkflowDefinition workflow, Map<String, Object> parameters) {
        log.info("Executing deploy_workflow tool");

        if (workflow == null) {
            throw new IllegalArgumentException("Workflow is required");
        }

        String workflowId = (String) parameters.get("workflowId");
        
        // Generate BPMN XML
        String bpmnXml = bpmnGenerator.generateBpmnXml(workflow);
        
        // Deploy to Flowable
        String deploymentId = flowableDeploymentService.deployProcessDefinition(
                workflow.getName(),
                bpmnXml
        );

        log.info("Workflow deployed successfully. Deployment ID: {}", deploymentId);
        
        return Map.of(
                "success", true,
                "deploymentId", deploymentId,
                "workflowName", workflow.getName(),
                "workflowId", workflowId,
                "bpmnXmlLength", bpmnXml.length()
        );
    }
}
