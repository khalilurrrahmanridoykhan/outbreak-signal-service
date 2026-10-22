package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static final OutbreakProperties.RateLimit SMALL_BUCKET = new OutbreakProperties.RateLimit(60, 3);

    @Test
    void allowsRequestsUpToTheBurstCapacityThenRejects() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(propertiesWith(SMALL_BUCKET));
        FilterChain chain = (req, res) -> {
        };

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestFrom("10.0.0.1"), response, chain);
            assertThat(response.getStatus()).as("request %d should be allowed", i + 1).isEqualTo(200);
        }

        MockHttpServletResponse fourth = new MockHttpServletResponse();
        filter.doFilter(requestFrom("10.0.0.1"), fourth, chain);
        assertThat(fourth.getStatus()).isEqualTo(429);
        assertThat(fourth.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void tracksEachRemoteAddressSeparately() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(propertiesWith(SMALL_BUCKET));
        FilterChain chain = (req, res) -> {
        };

        for (int i = 0; i < 3; i++) {
            filter.doFilter(requestFrom("10.0.0.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse firstAddressExhausted = new MockHttpServletResponse();
        filter.doFilter(requestFrom("10.0.0.1"), firstAddressExhausted, chain);
        assertThat(firstAddressExhausted.getStatus()).isEqualTo(429);

        // A different address has its own, untouched bucket.
        MockHttpServletResponse secondAddress = new MockHttpServletResponse();
        filter.doFilter(requestFrom("10.0.0.2"), secondAddress, chain);
        assertThat(secondAddress.getStatus()).isEqualTo(200);
    }

    @Test
    void exemptsHealthMetadataAndDocsEndpointsRegardlessOfRateLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(propertiesWith(new OutbreakProperties.RateLimit(60, 1)));
        FilterChain chain = (req, res) -> {
        };

        // Exhaust the one-token bucket for this address first.
        filter.doFilter(requestFrom("10.0.0.3"), new MockHttpServletResponse(), chain);

        for (String path : new String[] {"/actuator/health", "/fhir/metadata", "/swagger-ui/index.html", "/v3/api-docs"}) {
            MockHttpServletRequest request = requestFrom("10.0.0.3");
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).as("exempt path %s", path).isEqualTo(200);
        }
    }

    private static MockHttpServletRequest requestFrom(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alerts");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static OutbreakProperties propertiesWith(OutbreakProperties.RateLimit rateLimit) {
        return new OutbreakProperties(
                new OutbreakProperties.Sync(104, "0 0 3 * * *"),
                null,
                new OutbreakProperties.Detection(
                        new OutbreakProperties.Detection.MovingAverage(12, 3.0),
                        new OutbreakProperties.Detection.Ears(7, 3.0),
                        new OutbreakProperties.Detection.Cusum(8, 0.5, 5.0)),
                new OutbreakProperties.Security("http://localhost:8081/realms/outbreak-signal/protocol/openid-connect/certs"),
                new OutbreakProperties.Notification(
                        new OutbreakProperties.Notification.Outbox(5000, 20),
                        new OutbreakProperties.Notification.Webhook(5000, 20, 5, 10000, 3.0, 300000, 10000),
                        new OutbreakProperties.Notification.Email(false, "ops@example.org", "outbreak-signal@example.org")),
                rateLimit,
                new OutbreakProperties.Request(1_048_576));
    }
}
