package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.workflow.model.WorkflowDefinition;

import java.util.Map;

/**
 * Base interface for all workflow tools
 */
public interface WorkflowTool {

    String getName();

    String getDescription();

    Object execute(WorkflowDefinition workflow, Map<String, Object> parameters);
}
