package com.aiworkflow.designer.tools;

import com.aiworkflow.designer.telemetry.RequestTelemetry;
import com.aiworkflow.designer.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Adapts {@link WorkflowTool} implementations to Spring AI {@link FunctionCallback}s
 * so the LLM can invoke them via function calling. Tools mutate the workflow held
 * in {@code workflowRef}; tools that create/replace a workflow publish it there too.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowToolCallbacks {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> TOOL_SCHEMAS = Map.ofEntries(
            Map.entry("create_workflow", """
                    {"type":"object","properties":{
                      "name":{"type":"string","description":"Workflow name"},
                      "description":{"type":"string","description":"Workflow description"},
                      "forceReplace":{"type":"boolean","description":"Only set true when the user explicitly asked to start over on a non-empty workflow"}},
                     "required":["name","description"]}"""),
            Map.entry("add_task", """
                    {"type":"object","properties":{
                      "taskId":{"type":"string","description":"Unique task id, lowercase with underscores"},
                      "taskType":{"type":"string","enum":["USER_TASK","SERVICE_TASK","SCRIPT_TASK","BUSINESS_RULE_TASK","SEND_TASK","RECEIVE_TASK","MANUAL_TASK"]},
                      "taskName":{"type":"string","description":"Human-readable task name"},
                      "assignee":{"type":"string","description":"USER_TASK: user responsible for the task"},
                      "candidateUsers":{"type":"string","description":"USER_TASK: comma-separated candidate users"},
                      "candidateGroups":{"type":"string","description":"USER_TASK: comma-separated candidate groups, e.g. 'managers'"},
                      "serviceClass":{"type":"string","description":"SERVICE_TASK: fully-qualified Java class implementing the task"},
                      "expression":{"type":"string","description":"SERVICE_TASK: expression, e.g. ${leaveService.checkBalance(execution)}"},
                      "delegateExpression":{"type":"string","description":"SERVICE_TASK: delegate expression, e.g. ${leaveBalanceChecker}"},
                      "scriptFormat":{"type":"string","description":"SCRIPT_TASK: script language, e.g. javascript"},
                      "script":{"type":"string","description":"SCRIPT_TASK: inline script body"}},
                     "required":["taskType","taskName"]}"""),
            Map.entry("update_task", """
                    {"type":"object","properties":{
                      "taskId":{"type":"string","description":"Id of the existing task to update"},
                      "taskType":{"type":"string","enum":["USER_TASK","SERVICE_TASK","SCRIPT_TASK","BUSINESS_RULE_TASK","SEND_TASK","RECEIVE_TASK","MANUAL_TASK"]},
                      "taskName":{"type":"string"},
                      "assignee":{"type":"string"},
                      "candidateUsers":{"type":"string"},
                      "candidateGroups":{"type":"string"},
                      "serviceClass":{"type":"string"},
                      "expression":{"type":"string"},
                      "delegateExpression":{"type":"string"},
                      "scriptFormat":{"type":"string"},
                      "script":{"type":"string"}},
                     "required":["taskId"]}"""),
            Map.entry("add_gateway", """
                    {"type":"object","properties":{
                      "gatewayId":{"type":"string","description":"Unique gateway id"},
                      "gatewayType":{"type":"string","enum":["EXCLUSIVE_GATEWAY","PARALLEL_GATEWAY","INCLUSIVE_GATEWAY","EVENT_BASED_GATEWAY","COMPLEX_GATEWAY"]},
                      "gatewayName":{"type":"string"}},
                     "required":["gatewayType","gatewayName"]}"""),
            Map.entry("add_event", """
                    {"type":"object","properties":{
                      "eventId":{"type":"string","description":"Unique event id"},
                      "eventType":{"type":"string","enum":["START_EVENT","END_EVENT","TIMER_EVENT","MESSAGE_EVENT","ERROR_EVENT","SIGNAL_EVENT","INTERMEDIATE_EVENT"]},
                      "eventName":{"type":"string"},
                      "eventDefinition":{"type":"string","description":"e.g. timer expression"}},
                     "required":["eventType","eventName"]}"""),
            Map.entry("connect_nodes", """
                    {"type":"object","properties":{
                      "sourceId":{"type":"string","description":"Id of the source node"},
                      "targetId":{"type":"string","description":"Id of the target node"},
                      "condition":{"type":"string","description":"Optional flow condition"}},
                     "required":["sourceId","targetId"]}"""),
            Map.entry("add_condition", """
                    {"type":"object","properties":{
                      "edgeId":{"type":"string","description":"Id of the existing sequence flow to update"},
                      "sourceId":{"type":"string","description":"Source node id (alternative to edgeId)"},
                      "targetId":{"type":"string","description":"Target node id (alternative to edgeId)"},
                      "condition":{"type":"string","description":"Flow condition expression"}},
                     "required":["condition"]}"""),
            Map.entry("remove_task", """
                    {"type":"object","properties":{
                      "taskId":{"type":"string","description":"Id of the task to remove (task nodes only)"}},
                     "required":["taskId"]}"""),
            Map.entry("update_workflow", """
                    {"type":"object","properties":{
                      "name":{"type":"string"},
                      "description":{"type":"string"},
                      "variables":{"type":"object"},
                      "metadata":{"type":"object"}}}"""),
            Map.entry("validate_workflow", "{\"type\":\"object\",\"properties\":{}}"),
            Map.entry("get_workflow", "{\"type\":\"object\",\"properties\":{}}"),
            Map.entry("explain_workflow", "{\"type\":\"object\",\"properties\":{}}"),
            Map.entry("generate_bpmn_xml", "{\"type\":\"object\",\"properties\":{}}")
    );

    /** Tools that change the workflow structure/name when they run. */
    private static final List<String> MUTATING_TOOLS = List.of(
            "create_workflow", "add_task", "update_task", "add_gateway", "add_event",
            "connect_nodes", "add_condition", "remove_task", "update_workflow");

    private static final Map<String, List<String>> REQUIRED_PARAMS = Map.of(
            "create_workflow", List.of("name", "description"),
            "add_task", List.of("taskType", "taskName"),
            "update_task", List.of("taskId"),
            "add_gateway", List.of("gatewayType", "gatewayName"),
            "add_event", List.of("eventType", "eventName"),
            "connect_nodes", List.of("sourceId", "targetId"),
            "add_condition", List.of("condition"),
            "remove_task", List.of("taskId"));

    private static final int MAX_TOTAL_CALLS = 40;
    private static final int MAX_IDENTICAL_CALLS = 2;

    private final ToolRegistry toolRegistry;

    /**
     * Builds function callbacks for the given tool names. Mutations are applied to
     * {@code workflowRef}; {@code mutated} is set to true when a mutating tool ran.
     * Includes guards against weak models emitting malformed or runaway tool calls.
     */
    public FunctionCallback[] build(List<String> toolNames,
                                    AtomicReference<WorkflowDefinition> workflowRef,
                                    AtomicBoolean mutated) {
        Map<String, Integer> callCounts = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicInteger totalCalls =
                new java.util.concurrent.atomic.AtomicInteger();

        List<FunctionCallback> callbacks = new ArrayList<>();
        for (String name : toolNames) {
            if (!toolRegistry.hasTool(name)) {
                log.warn("Skipping unknown tool: {}", name);
                continue;
            }
            callbacks.add(toCallback(toolRegistry.getTool(name), workflowRef, mutated,
                    callCounts, totalCalls));
        }
        return callbacks.toArray(new FunctionCallback[0]);
    }

    public List<String> designToolNames() {
        return List.of("create_workflow", "add_task", "update_task", "add_gateway", "add_event",
                "connect_nodes", "add_condition", "remove_task", "update_workflow",
                "validate_workflow", "get_workflow", "explain_workflow", "generate_bpmn_xml");
    }

    private FunctionCallback toCallback(WorkflowTool tool,
                                        AtomicReference<WorkflowDefinition> workflowRef,
                                        AtomicBoolean mutated,
                                        Map<String, Integer> callCounts,
                                        java.util.concurrent.atomic.AtomicInteger totalCalls) {
        Function<Map<String, Object>, Object> fn = rawParams -> {
            Map<String, Object> params = sanitize(rawParams);
            log.info("LLM invoking tool {} with params {}", tool.getName(), params);

            if (totalCalls.incrementAndGet() > MAX_TOTAL_CALLS) {
                return "ERROR: tool call budget exhausted. Stop calling tools and give your final answer.";
            }

            List<String> missing = REQUIRED_PARAMS.getOrDefault(tool.getName(), List.of())
                    .stream()
                    .filter(key -> {
                        Object v = params.get(key);
                        return v == null || v.toString().isBlank();
                    })
                    .toList();
            if (!missing.isEmpty()) {
                return "ERROR: missing required parameter(s) " + missing
                        + " for tool " + tool.getName()
                        + ". Retry once with valid JSON arguments.";
            }

            String signature = tool.getName() + canonical(params);
            if (callCounts.merge(signature, 1, Integer::sum) > MAX_IDENTICAL_CALLS) {
                return "ERROR: duplicate identical tool call ignored. Do not call "
                        + tool.getName() + " with these parameters again - continue with the next step.";
            }

            long startedNanos = System.nanoTime();
            try {
                Object result = tool.execute(workflowRef.get(), params);
                if (result instanceof WorkflowDefinition newWorkflow) {
                    workflowRef.set(newWorkflow);
                }
                if (MUTATING_TOOLS.contains(tool.getName())) {
                    // Central post-mutation check: catch corruption early and
                    // report it back to the model instead of persisting it.
                    List<String> violations = GraphInvariants.check(workflowRef.get());
                    if (!violations.isEmpty()) {
                        log.warn("Tool {} left graph violations: {}", tool.getName(), violations);
                        RequestTelemetry.recordToolCall(tool.getName(), params, false,
                                elapsedMs(startedNanos), violations.toString());
                        return "ERROR: " + tool.getName() + " left the workflow inconsistent: "
                                + violations + ". Fix the graph before continuing.";
                    }
                    mutated.set(true);
                }
                RequestTelemetry.recordToolCall(tool.getName(), params, true,
                        elapsedMs(startedNanos), null);
                return result;
            } catch (Exception e) {
                RequestTelemetry.recordToolCall(tool.getName(), params, false,
                        elapsedMs(startedNanos), e.getMessage());
                log.warn("Tool {} failed: {}", tool.getName(), e.getMessage());
                return "ERROR: tool " + tool.getName() + " failed: " + e.getMessage()
                        + ". Fix the arguments or move to the next step.";
            }
        };

        String schema = TOOL_SCHEMAS.getOrDefault(tool.getName(),
                "{\"type\":\"object\",\"properties\":{}}");

        return FunctionCallback.builder()
                .description(tool.getDescription())
                .inputTypeSchema(schema)
                .responseConverter(result -> {
                    try {
                        return MAPPER.writeValueAsString(result);
                    } catch (Exception e) {
                        return String.valueOf(result);
                    }
                })
                .function(tool.getName(), fn)
                .inputType(Map.class)
                .build();
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /**
     * Canonical parameter serialization for duplicate-call detection — sorts keys
     * so the same arguments in a different order still count as identical.
     */
    private String canonical(Map<String, Object> params) {
        try {
            return MAPPER.writeValueAsString(new java.util.TreeMap<>(params));
        } catch (Exception e) {
            return String.valueOf(params);
        }
    }

    /**
     * Cleans LLM-emitted arguments: trims keys and strips stray backticks,
     * quotes and whitespace from string values. Weak local models frequently
     * emit artifacts like {@code end_rejected_manager`} or empty values.
     */
    private Map<String, Object> sanitize(Map<String, Object> params) {
        if (params == null) {
            return Map.of();
        }
        Map<String, Object> clean = new java.util.LinkedHashMap<>();
        params.forEach((key, value) -> {
            String cleanKey = key == null ? "" : key.trim();
            if (cleanKey.isEmpty()) {
                return;
            }
            if (value instanceof String s) {
                clean.put(cleanKey, s.replace("`", "").trim());
            } else {
                clean.put(cleanKey, value);
            }
        });
        return clean;
    }
}
