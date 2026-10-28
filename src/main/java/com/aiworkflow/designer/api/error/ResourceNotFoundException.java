package com.aiworkflow.designer.api.error;

/**
 * Thrown when a resource does not exist or the caller is not allowed to see
 * it. Mapped to HTTP 404 — a uniform response for missing and non-owned
 * resources prevents resource-ID enumeration.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
