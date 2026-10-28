package com.aiworkflow.designer.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolRegistry {

    private final List<WorkflowTool> tools;
    private Map<String, WorkflowTool> toolMap;

    @jakarta.annotation.PostConstruct
    public void initialize() {
        toolMap = tools.stream()
                .collect(Collectors.toMap(WorkflowTool::getName, Function.identity()));

        log.info("Tool registry initialized with {} tools", toolMap.size());
        log.info("Available tools: {}", toolMap.keySet());
    }

    public WorkflowTool getTool(String toolName) {
        WorkflowTool tool = toolMap.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }
        return tool;
    }

    public Map<String, WorkflowTool> getAllTools() {
        return Map.copyOf(toolMap);
    }

    public List<String> getToolNames() {
        return new ArrayList<>(toolMap.keySet());
    }

    public boolean hasTool(String toolName) {
        return toolMap.containsKey(toolName);
    }
}
