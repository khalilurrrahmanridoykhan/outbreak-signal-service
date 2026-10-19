package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@WebMvcTest(controllers = DetectionController.class)
@Import({SecurityConfig.class, DetectionControllerTest.StubJwtBeans.class})
class DetectionControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DetectionService detectionService;

    @Test
    void triggeringDetectionWithNoTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/detection/run")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAnalystCannotTriggerDetection() throws Exception {
        mvc.perform(post("/api/v1/detection/run").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanTriggerDetection() throws Exception {
        when(detectionService.runDetection()).thenReturn(new DetectionSummary(List.of()));

        mvc.perform(post("/api/v1/detection/run").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
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
