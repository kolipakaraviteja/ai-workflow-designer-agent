package com.aiworkflow.designer.document.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple per-user sliding-window rate limiter for expensive operations
 * (document upload/extraction, knowledge ingestion). In-memory only — good
 * enough for a single-node deployment; swap for a shared store if the app
 * ever runs multi-node.
 */
@Component
public class UploadRateLimiter {

    /** Max uploads per user within the window. */
    private static final int MAX_UPLOADS = 10;
    /** Window length in milliseconds (1 minute). */
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> buckets =
            new ConcurrentHashMap<>();

    /**
     * @throws RateLimitExceededException when the user exceeded the upload quota
     */
    public void checkUpload(String username) {
        String key = username != null ? username : "anonymous";
        ConcurrentLinkedDeque<Long> hits = buckets.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        while (true) {
            Long head = hits.peekFirst();
            if (head == null || now - head < WINDOW_MS) {
                break;
            }
            hits.pollFirst();
        }
        if (hits.size() >= MAX_UPLOADS) {
            throw new RateLimitExceededException(
                    "Upload rate limit exceeded — max " + MAX_UPLOADS + " uploads per minute");
        }
        hits.addLast(now);
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
