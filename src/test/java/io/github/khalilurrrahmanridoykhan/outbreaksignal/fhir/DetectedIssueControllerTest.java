package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertNotFoundException;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.security.SecurityConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers content negotiation (real FHIR JSON via {@link FhirJsonHttpMessageConverter},
 * not Jackson), security (DetectedIssue needs a token, metadata does not),
 * and the FHIR-shaped {@code 404} ({@link FhirExceptionHandler}'s
 * {@code OperationOutcome}, not the REST API's {@code ProblemDetail}) — the
 * real {@link SecurityConfig} filter chain is imported, same as
 * {@code AlertControllerTest}.
 */
@WebMvcTest(controllers = {DetectedIssueController.class, FhirMetadataController.class})
@Import({FhirConfig.class, FhirJsonHttpMessageConverter.class, FhirWebMvcConfig.class, FhirExceptionHandler.class,
        CapabilityStatementFactory.class, SecurityConfig.class, DetectedIssueControllerTest.TestBeans.class})
class DetectedIssueControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private FhirAlertService fhirAlertService;

    private static DetectedIssue sampleIssue() {
        DetectedIssue issue = new DetectedIssue();
        issue.setId("62");
        issue.setStatus(DetectedIssue.DetectedIssueStatus.PRELIMINARY);
        return issue;
    }

    @Test
    void readingWithNoTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/fhir/DetectedIssue/62")).andExpect(status().isUnauthorized());
    }

    @Test
    void readingAnExistingIssueReturnsRealFhirJson() throws Exception {
        when(fhirAlertService.findById(62L)).thenReturn(sampleIssue());

        mvc.perform(get("/fhir/DetectedIssue/62").with(jwt()).header("Accept", "application/fhir+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/fhir+json"))
                .andExpect(jsonPath("$.resourceType").value("DetectedIssue"))
                .andExpect(jsonPath("$.id").value("62"))
                .andExpect(jsonPath("$.status").value("preliminary"));
    }

    @Test
    void plainApplicationJsonIsAlsoAccepted() throws Exception {
        when(fhirAlertService.findById(62L)).thenReturn(sampleIssue());

        mvc.perform(get("/fhir/DetectedIssue/62").with(jwt()).header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.resourceType").value("DetectedIssue"));
    }

    @Test
    void anUnknownIdReturnsAnOperationOutcomeNotAProblemDetail() throws Exception {
        when(fhirAlertService.findById(99L)).thenThrow(new AlertNotFoundException(99L));

        mvc.perform(get("/fhir/DetectedIssue/99").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("not-found"));
    }

    @Test
    void searchingWithNoTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/fhir/DetectedIssue")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchingReturnsASearchsetBundle() throws Exception {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(1);
        bundle.addEntry().setResource(sampleIssue());
        when(fhirAlertService.search(eq("preliminary"), any(), any())).thenReturn(bundle);

        mvc.perform(get("/fhir/DetectedIssue?status=preliminary").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("searchset"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void metadataNeedsNoTokenAndListsExactlyDetectedIssue() throws Exception {
        mvc.perform(get("/fhir/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("CapabilityStatement"))
                .andExpect(jsonPath("$.rest[0].resource[0].type").value("DetectedIssue"));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);
        }

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
