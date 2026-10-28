package com.aiworkflow.designer.telemetry;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-request telemetry collected while the agent pipeline runs: the ordered
 * tool-call trace, the agent routing path, and every LLM call's token usage.
 * All state is thread-local — pooled threads must call {@link #clear()} at
 * request end (done by {@code CorrelationIdFilter} and {@code AgentController}).
 */
public final class RequestTelemetry {

    public static final String MDC_CORRELATION_ID = "correlationId";

    private static final int MAX_ARG_VALUE_LENGTH = 200;

    private RequestTelemetry() {
    }

    /** One executed tool invocation, in call order. */
    public record ToolCall(int seq, String name, Map<String, Object> args,
                           boolean success, long durationMs, String error) {
    }

    /** One LLM chat call with its reported token usage (nulls for local models). */
    public record LlmCall(String agentType, String model,
                          Long promptTokens, Long completionTokens, Long totalTokens) {
    }

    /** Immutable view of everything collected during the request. */
    public record Snapshot(String correlationId, List<String> agentPath,
                           List<ToolCall> toolCalls, List<LlmCall> llmCalls,
                           UUID userId, String username,
                           UUID workflowId, UUID conversationId) {

        public int toolCallCount() {
            return toolCalls.size();
        }

        public String agentPathString() {
            return String.join("→", agentPath);
        }

        public long promptTokens() {
            return llmCalls.stream().mapToLong(c -> c.promptTokens() != null ? c.promptTokens() : 0).sum();
        }

        public long completionTokens() {
            return llmCalls.stream().mapToLong(c -> c.completionTokens() != null ? c.completionTokens() : 0).sum();
        }

        public long totalTokens() {
            return llmCalls.stream().mapToLong(c -> c.totalTokens() != null ? c.totalTokens() : 0).sum();
        }
    }

    private static final class State {
        UUID userId;
        String username;
        UUID workflowId;
        UUID conversationId;
        final List<String> agentPath = new ArrayList<>();
        final List<ToolCall> toolCalls = new ArrayList<>();
        final List<LlmCall> llmCalls = new ArrayList<>();
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    /** Called by AgentController once the caller is authenticated. */
    public static void setContext(UUID userId, String username, UUID workflowId, UUID conversationId) {
        State s = STATE.get();
        s.userId = userId;
        s.username = username;
        s.workflowId = workflowId;
        s.conversationId = conversationId;
    }

    public static String correlationId() {
        String id = MDC.get(MDC_CORRELATION_ID);
        return id != null ? id : "none";
    }

    /** Records that an agent began processing — builds the routing path. */
    public static void recordAgent(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return;
        }
        STATE.get().agentPath.add(agentType);
    }

    /** Records one tool invocation. Args are sanitized (values truncated). */
    public static void recordToolCall(String name, Map<String, Object> args,
                                      boolean success, long durationMs, String error) {
        State s = STATE.get();
        s.toolCalls.add(new ToolCall(s.toolCalls.size() + 1, name,
                sanitizeArgs(args), success, durationMs,
                error != null && error.length() > MAX_ARG_VALUE_LENGTH
                        ? error.substring(0, MAX_ARG_VALUE_LENGTH) : error));
    }

    /** Records one LLM call's token usage. Null usage fields are kept as null. */
    public static void recordLlmCall(String agentType, String model,
                                     Long promptTokens, Long completionTokens, Long totalTokens) {
        STATE.get().llmCalls.add(
                new LlmCall(agentType, model, promptTokens, completionTokens, totalTokens));
    }

    public static Snapshot snapshot() {
        State s = STATE.get();
        return new Snapshot(correlationId(), List.copyOf(s.agentPath),
                List.copyOf(s.toolCalls), List.copyOf(s.llmCalls),
                s.userId, s.username, s.workflowId, s.conversationId);
    }

    public static void clear() {
        STATE.remove();
    }

    private static Map<String, Object> sanitizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new java.util.LinkedHashMap<>();
        args.forEach((key, value) -> {
            if (value == null) {
                clean.put(key, null);
            } else {
                String text = String.valueOf(value);
                clean.put(key, text.length() > MAX_ARG_VALUE_LENGTH
                        ? text.substring(0, MAX_ARG_VALUE_LENGTH) + "…" : text);
            }
        });
        return clean;
    }
}
