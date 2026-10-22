package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a correlation id in MDC for the life of one HTTP request, so every
 * log line it produces — across every layer, not just the controller — can
 * be grouped under {@code logging.structured.format.console: ecs}'s
 * per-line MDC fields. Reuses an incoming {@code X-Correlation-Id} if a
 * caller (a gateway, another service) already set one, so a trace started
 * upstream is not discarded; generates a fresh one otherwise. The scheduled
 * sync's own correlation id, set by {@code Dhis2SyncScheduler}, uses the
 * same MDC key so log lines from a sync look exactly like log lines from a
 * request in every other respect.
 *
 * <p>Not a {@code @Component}: registered explicitly by {@code FilterConfig}
 * ahead of Spring Security's own filter chain, the same reasoning as
 * {@link RateLimitFilter}'s javadoc — a request Security itself rejects
 * (an expired or missing token) should still get a correlation id on its
 * {@code 401} log line.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    private static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
