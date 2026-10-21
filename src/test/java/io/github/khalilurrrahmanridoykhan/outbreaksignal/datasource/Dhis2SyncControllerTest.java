package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir.FhirConfig;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// FhirConfig is imported because @WebMvcTest auto-detects every
// HttpMessageConverter bean application-wide (the same way it always
// auto-detects WebMvcConfigurer beans), regardless of which controller is
// under test — FhirJsonHttpMessageConverter needs a FhirContext even here.
@WebMvcTest(controllers = Dhis2SyncController.class)
@Import({SecurityConfig.class, FhirConfig.class, Dhis2SyncControllerTest.StubJwtBeans.class})
class Dhis2SyncControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private Dhis2SyncService syncService;

    @Test
    void triggeringSyncWithNoTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/sync")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAnalystCannotTriggerSync() throws Exception {
        mvc.perform(post("/api/v1/sync").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aViewerCannotTriggerSync() throws Exception {
        mvc.perform(post("/api/v1/sync").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanTriggerSync() throws Exception {
        when(syncService.sync()).thenReturn(new SyncResult(0, 104, List.of()));

        mvc.perform(post("/api/v1/sync").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
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
