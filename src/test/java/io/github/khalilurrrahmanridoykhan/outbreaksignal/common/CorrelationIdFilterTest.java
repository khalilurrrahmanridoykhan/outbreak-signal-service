package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAFreshCorrelationIdWhenNoneWasSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInMdc = new String[1];
        FilterChain chain = (req, res) -> seenInMdc[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(seenInMdc[0]).isNotBlank();
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seenInMdc[0]);
        // Removed once the request finishes, so it can never leak into an unrelated later log line.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reusesAnIncomingCorrelationIdInsteadOfGeneratingANewOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "from-upstream-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInMdc = new String[1];
        FilterChain chain = (req, res) -> seenInMdc[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(seenInMdc[0]).isEqualTo("from-upstream-123");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("from-upstream-123");
    }

    @Test
    void clearsMdcEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(request, response, chain));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
