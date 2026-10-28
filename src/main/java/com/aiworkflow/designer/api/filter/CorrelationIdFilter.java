package com.aiworkflow.designer.api.filter;

import com.aiworkflow.designer.telemetry.RequestTelemetry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads (or generates) an {@code X-Correlation-Id} per request, places it in
 * the logging MDC so every log line carries it, and echoes it back on the
 * response. Also clears request telemetry so pooled threads never leak traces
 * between requests.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else if (correlationId.length() > 64) {
            correlationId = correlationId.substring(0, 64);
        }

        MDC.put(RequestTelemetry.MDC_CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestTelemetry.MDC_CORRELATION_ID);
            RequestTelemetry.clear();
        }
    }
}
