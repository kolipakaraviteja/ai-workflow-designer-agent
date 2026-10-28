package com.aiworkflow.designer.conversation.memory;

import java.util.UUID;

/**
 * Thread-local holder for the current request's workflow/user context so that
 * chat-memory writes performed by advisors can be attributed correctly.
 */
public final class ConversationContext {

    private static final ThreadLocal<UUID> WORKFLOW_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();

    private ConversationContext() {
    }

    public static void set(UUID workflowId, UUID userId) {
        WORKFLOW_ID.set(workflowId);
        USER_ID.set(userId);
    }

    public static UUID getWorkflowId() {
        return WORKFLOW_ID.get();
    }

    public static UUID getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        WORKFLOW_ID.remove();
        USER_ID.remove();
    }
}
