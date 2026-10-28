package com.aiworkflow.designer.runtime.flowable;

import com.aiworkflow.designer.persistence.repository.DeploymentRepository;
import com.aiworkflow.designer.persistence.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.validation.ProcessValidator;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ValidationError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowableDeploymentService {

    private final RepositoryService repositoryService;
    private final DeploymentRepository deploymentRepository;
    private final WorkflowRepository workflowRepository;

    @Transactional
    public String deployProcessDefinition(String processName, String bpmnXml) {
        log.info("Deploying process definition: {}", processName);

        DeploymentBuilder deploymentBuilder = repositoryService.createDeployment()
                .name(processName)
                .addString(processName + ".bpmn20.xml", bpmnXml);

        org.flowable.engine.repository.Deployment flowableDeployment = deploymentBuilder.deploy();

        log.info("Process definition deployed successfully. Deployment ID: {}", flowableDeployment.getId());
        return flowableDeployment.getId();
    }

    /**
     * Verifies generated BPMN XML is well-formed and passes Flowable's process
     * validator before it is treated as deployable.
     *
     * @throws IllegalArgumentException listing the validation problems
     */
    public void verifyDeployable(String bpmnXml) {
        BpmnModel model;
        try {
            // Secure StAX reader — no DTDs, no external entities (XXE-safe)
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            model = new BpmnXMLConverter().convertToBpmnModel(reader);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Generated BPMN is not parseable: " + e.getMessage(), e);
        }

        ProcessValidator validator = new ProcessValidatorFactory().createDefaultProcessValidator();
        List<ValidationError> errors = validator.validate(model);
        if (errors != null && !errors.isEmpty()) {
            String problems = errors.stream()
                    .map(e -> e.getActivityId() + ": " + e.getProblem())
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Generated BPMN is not deployable: " + problems);
        }
    }

    /**
     * Undeploys a process definition. Cascade only when the caller explicitly
     * forced undeploy with active instances.
     */
    @Transactional
    public void undeployProcessDefinition(String deploymentId) {
        undeployProcessDefinition(deploymentId, false);
    }

    @Transactional
    public void undeployProcessDefinition(String deploymentId, boolean cascade) {
        log.info("Undeploying process definition: {} (cascade={})", deploymentId, cascade);

        try {
            repositoryService.deleteDeployment(deploymentId, cascade);
            log.info("Process definition undeployed successfully: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to undeploy process definition", e);
            throw new RuntimeException("Failed to undeploy process definition", e);
        }
    }

    public String extractProcessDefinitionKey(String deploymentId) {
        // Extract the process definition key from the deployment
        return repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .singleResult()
                .getKey();
    }
}
