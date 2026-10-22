package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects a request whose declared {@code Content-Length} exceeds
 * {@code outbreak.request.max-body-bytes}, before anything downstream reads
 * the body. A request with no {@code Content-Length} (chunked transfer
 * encoding) is not checked here — nothing in this API's own clients sends
 * chunked requests, and catching that case would need wrapping the input
 * stream to count bytes as they arrive, not worth it for a service with no
 * file-upload endpoint.
 *
 * <p>Not a {@code @Component}: see {@link RateLimitFilter}'s javadoc for why
 * these are registered explicitly, by {@code FilterConfig}, ahead of Spring
 * Security's own filter chain instead.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    public RequestSizeLimitFilter(OutbreakProperties properties) {
        this.maxBodyBytes = properties.request().maxBodyBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            response.setStatus(413);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":413,\"title\":\"Payload Too Large\",\"detail\":\"Request body exceeds the configured limit.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
