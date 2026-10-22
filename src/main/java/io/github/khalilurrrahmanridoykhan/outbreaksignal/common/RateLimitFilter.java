package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A token bucket per remote address, refilled continuously at
 * {@code outbreak.rate-limit.requests-per-minute} up to
 * {@code .burst-capacity} tokens. Health, metadata and docs endpoints are
 * exempt — the same reasoning {@code SecurityConfig} already applies to them
 * (infra, not alert data, and a load balancer needs the health check always
 * reachable).
 *
 * <p>Keyed by remote address rather than by authenticated subject: running
 * before Spring Security's own filters is what makes this simple to reason
 * about and test in isolation, at the cost of not distinguishing two
 * authenticated users behind the same address (for example, a shared NAT) —
 * an acceptable trade for a demo-scale service, not a production multi-tenant
 * API gateway.
 *
 * <p>Not a {@code @Component}: a plain component-scanned {@code Filter} bean
 * is auto-registered by Boot at {@code Ordered.LOWEST_PRECEDENCE}, which runs
 * <em>after</em> Spring Security's own filter chain — exactly backwards for a
 * rate limiter, which should reject abusive traffic before spending any
 * effort validating its JWT. {@code FilterConfig} registers this (and
 * {@link RequestSizeLimitFilter}) explicitly, ordered ahead of Security's
 * chain instead.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final OutbreakProperties.RateLimit config;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(OutbreakProperties properties) {
        this.config = properties.rateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.equals("/fhir/metadata")
                || path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TokenBucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(),
                addr -> new TokenBucket(config.burstCapacity(), config.requestsPerMinute() / 60.0));
        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Rate limit exceeded; slow down and retry.\"}");
    }

    /** Continuous refill (not fixed windows), so a client never gets a full burst again right at a window boundary. */
    private static final class TokenBucket {

        private final double capacity;
        private final double tokensPerSecond;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(double capacity, double tokensPerSecond) {
            this.capacity = capacity;
            this.tokensPerSecond = tokensPerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(capacity, tokens + elapsedSeconds * tokensPerSecond);
        }
    }
}
