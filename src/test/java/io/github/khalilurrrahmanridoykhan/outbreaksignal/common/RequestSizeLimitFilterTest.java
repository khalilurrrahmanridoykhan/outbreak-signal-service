package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(
            new OutbreakProperties(null, null, null, null, null, null, new OutbreakProperties.Request(1000)));

    @Test
    void rejectsABodyOverTheLimitWithoutReachingTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhook-subscriptions");
        request.setContentType("application/json");
        request.setContent(new byte[1001]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("Payload Too Large");
        assertThat(chainCalled[0]).isFalse();
    }

    @Test
    void allowsABodyAtOrUnderTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhook-subscriptions");
        request.setContentType("application/json");
        request.setContent(new byte[1000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;

        filter.doFilter(request, response, chain);

        assertThat(chainCalled[0]).isTrue();
    }
}
