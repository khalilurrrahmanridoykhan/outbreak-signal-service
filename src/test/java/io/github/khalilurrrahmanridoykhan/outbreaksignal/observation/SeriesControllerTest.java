package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Also what caught {@code ObservationExceptionHandler}'s own version of the
 * advice-ordering bug fixed via {@code AlertControllerTest} — before that
 * fix, {@code SeriesNotFoundException} resolved to a generic 500 here too.
 */
@WebMvcTest(controllers = SeriesController.class)
@Import({ObservationExceptionHandler.class, SecurityConfig.class, SeriesControllerTest.StubJwtBeans.class})
class SeriesControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SeriesService seriesService;

    @Test
    void listingSeriesWithNoTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/series")).andExpect(status().isUnauthorized());
    }

    @Test
    void aViewerCanListSeries() throws Exception {
        when(seriesService.list(org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/series").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownSeriesIsNotFound() throws Exception {
        when(seriesService.observations(99L)).thenThrow(new SeriesNotFoundException(99L));

        mvc.perform(get("/api/v1/series/99/observations").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class StubJwtBeans {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("not used in tests");
            };
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
