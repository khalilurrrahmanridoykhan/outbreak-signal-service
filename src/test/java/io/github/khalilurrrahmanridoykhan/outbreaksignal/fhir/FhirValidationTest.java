package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertSummary;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The plan's own "Done when" gate: every alert status maps to a valid
 * {@code DetectedIssue}, and {@code FhirInstanceValidator} reports zero
 * errors on a generated batch — one per (status, severity) combination,
 * validated against the base R4 profile (no project-specific
 * {@code StructureDefinition} is declared, so this is exactly that base
 * profile, plus this project's own open, undeclared extensions, which the
 * open extensibility model allows without error).
 */
class FhirValidationTest {

    private static FhirValidator validator;

    private final DetectedIssueMapper mapper = new DetectedIssueMapper();

    @BeforeAll
    static void setUpValidator() {
        FhirContext ctx = FhirContext.forR4();
        ValidationSupportChain supportChain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx));
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(supportChain);
        validator = ctx.newValidator();
        validator.registerValidatorModule(instanceValidator);
    }

    @Test
    void everyStatusAndSeverityCombinationValidatesWithZeroErrors() {
        long id = 1L;
        for (AlertStatus status : AlertStatus.values()) {
            for (Severity severity : Severity.values()) {
                AlertSummary alert = new AlertSummary(
                        id++, 100L, 7L, "Kpanga Kabonde", "IDSR Malaria", "EARS_C3",
                        LocalDate.of(2026, 1, 5), new BigDecimal("9.50"), new BigDecimal("3.00"),
                        severity, status, 0L, Instant.parse("2026-01-12T03:00:00Z"));
                DetectedIssue issue = mapper.map(alert);

                ValidationResult result = validator.validateWithResult(issue);
                assertThat(result.isSuccessful())
                        .as(() -> "status=%s severity=%s messages=%s".formatted(status, severity, result.getMessages()))
                        .isTrue();
            }
        }
    }
}
