package com.aiworkflow.designer.telemetry;

import com.aiworkflow.designer.persistence.entity.LlmUsage;
import com.aiworkflow.designer.persistence.repository.LlmUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finalizes per-request telemetry: persists one {@link LlmUsage} row per LLM
 * call and produces the usage summaries returned by the usage endpoints and
 * attached to {@code AgentResponse}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private final LlmUsageRepository llmUsageRepository;

    /** Persists every LLM call captured in the snapshot. Never throws. */
    @Transactional
    public void persistLlmUsage(RequestTelemetry.Snapshot snapshot) {
        if (snapshot == null || snapshot.llmCalls().isEmpty()) {
            return;
        }
        try {
            List<LlmUsage> rows = snapshot.llmCalls().stream()
                    .map(call -> LlmUsage.builder()
                            .userId(snapshot.userId())
                            .username(snapshot.username())
                            .workflowId(snapshot.workflowId())
                            .conversationId(snapshot.conversationId())
                            .correlationId(snapshot.correlationId())
                            .agentType(call.agentType())
                            .model(call.model())
                            .promptTokens(call.promptTokens())
                            .completionTokens(call.completionTokens())
                            .totalTokens(call.totalTokens())
                            .build())
                    .toList();
            llmUsageRepository.saveAll(rows);
        } catch (Exception e) {
            log.warn("Failed to persist LLM usage: {}", e.getMessage());
        }
    }

    /** Compact per-request summary attached to AgentResponse. */
    public Map<String, Object> requestSummary(RequestTelemetry.Snapshot snapshot) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("llmCalls", snapshot.llmCalls().size());
        summary.put("promptTokens", snapshot.promptTokens());
        summary.put("completionTokens", snapshot.completionTokens());
        summary.put("totalTokens", snapshot.totalTokens());
        return summary;
    }

    public Map<String, Object> summarize(List<LlmUsage> rows) {
        long prompt = 0;
        long completion = 0;
        long total = 0;
        Map<String, long[]> byWorkflow = new LinkedHashMap<>();
        for (LlmUsage row : rows) {
            prompt += row.getPromptTokens() != null ? row.getPromptTokens() : 0;
            completion += row.getCompletionTokens() != null ? row.getCompletionTokens() : 0;
            total += row.getTotalTokens() != null ? row.getTotalTokens() : 0;
            String key = row.getWorkflowId() != null ? row.getWorkflowId().toString() : "none";
            byWorkflow.computeIfAbsent(key, k -> new long[]{0, 0})[0]++;
            byWorkflow.get(key)[1] += row.getTotalTokens() != null ? row.getTotalTokens() : 0;
        }
        Map<String, Object> workflows = new LinkedHashMap<>();
        byWorkflow.forEach((id, counts) -> workflows.put(id, Map.of(
                "llmCalls", counts[0],
                "totalTokens", counts[1])));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("llmCalls", rows.size());
        summary.put("promptTokens", prompt);
        summary.put("completionTokens", completion);
        summary.put("totalTokens", total);
        summary.put("byWorkflow", workflows);
        return summary;
    }

    public List<LlmUsage> usageForUser(UUID userId) {
        return llmUsageRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<LlmUsage> usageForUsername(String username) {
        return llmUsageRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<LlmUsage> usageForWorkflow(UUID workflowId) {
        return llmUsageRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId);
    }
}
