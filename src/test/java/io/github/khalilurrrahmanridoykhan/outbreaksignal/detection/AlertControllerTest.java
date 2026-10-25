package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir.FhirConfig;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Series;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers validation/error shapes and, per role, which endpoints are allowed
 * or forbidden — the real {@link SecurityConfig} filter chain and method
 * security are imported so these checks exercise the actual authorization
 * rules, not framework defaults. The {@link JwtDecoder} stub below is never
 * actually invoked: {@code jwt()} post-processors build the authenticated
 * principal directly, bypassing decoding entirely, matching the plan's own
 * "spring-security-test JWT post-processors so CI needs no Keycloak."
 *
 * <p>{@code FhirConfig} is imported because {@code @WebMvcTest} auto-detects
 * every {@code HttpMessageConverter} bean application-wide, regardless of
 * which controller is under test — {@code FhirJsonHttpMessageConverter}
 * needs a {@code FhirContext} even here.
 */
@WebMvcTest(controllers = AlertController.class)
@Import({AlertExceptionHandler.class, SecurityConfig.class, FhirConfig.class, AlertControllerTest.StubJwtBeans.class})
class AlertControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AlertService alertService;

    @Test
    void listingAlertsWithNoTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    void aViewerCanListAlerts() throws Exception {
        when(alertService.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/alerts").with(jwt().authorities(authority("VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void aViewerCannotAcknowledge() throws Exception {
        mvc.perform(post("/api/v1/alerts/1/acknowledge").with(jwt().authorities(authority("VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnalystCanAcknowledgeAndTheJwtSubjectBecomesTheActor() throws Exception {
        when(alertService.acknowledge(eq(1L), anyString())).thenReturn(sampleAlertSummary());

        mvc.perform(post("/api/v1/alerts/1/acknowledge")
                        .with(jwt().authorities(authority("ANALYST")).jwt(builder -> builder.subject("analyst1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void anAdminCanAlsoAcknowledge() throws Exception {
        when(alertService.acknowledge(eq(1L), anyString())).thenReturn(sampleAlertSummary());

        mvc.perform(post("/api/v1/alerts/1/acknowledge").with(jwt().authorities(authority("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void dismissingWithABlankReasonIsRejectedAsAValidationError() throws Exception {
        mvc.perform(post("/api/v1/alerts/1/dismiss")
                        .with(jwt().authorities(authority("ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anIllegalTransitionIsAConflictCarryingFromAndTo() throws Exception {
        when(alertService.resolve(eq(1L), anyString()))
                .thenThrow(new IllegalAlertTransitionException(AlertStatus.OPEN, AlertStatus.RESOLVED));

        mvc.perform(post("/api/v1/alerts/1/resolve").with(jwt().authorities(authority("ANALYST"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.from").value("OPEN"))
                .andExpect(jsonPath("$.to").value("RESOLVED"));
    }

    @Test
    void anUnknownAlertIsNotFound() throws Exception {
        when(alertService.getDetail(99L)).thenThrow(new AlertNotFoundException(99L));

        mvc.perform(get("/api/v1/alerts/99").with(jwt().authorities(authority("VIEWER"))))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.security.core.authority.SimpleGrantedAuthority authority(String role) {
        return new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role);
    }

    private static AlertSummary sampleAlertSummary() {
        OrgUnit orgUnit = new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null);
        Series series = new Series(orgUnit, "vq2qO3eTrNi", "IDSR Malaria");
        DetectionRun run = new DetectionRun("EARS_C1", "{}", 104, Instant.now());
        Alert alert = new Alert(series, MONDAY, "EARS_C1", new BigDecimal("9.5"), new BigDecimal("3.0"),
                Severity.HIGH, run, Instant.now());
        alert.transitionTo(AlertStatus.ACKNOWLEDGED);
        return AlertSummary.from(alert);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class StubJwtBeans {

        @Bean
        JwtDecoder jwtDecoder() {
            // Never invoked: the jwt() post-processor sets the authenticated
            // principal directly and skips decoding entirely. It must exist
            // only because SecurityConfig.filterChain requires the bean.
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
