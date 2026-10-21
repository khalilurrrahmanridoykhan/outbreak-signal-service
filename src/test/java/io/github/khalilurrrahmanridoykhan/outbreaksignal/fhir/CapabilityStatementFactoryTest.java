package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.TypeRestfulInteraction;
import org.junit.jupiter.api.Test;

/** The plan's "an honest CapabilityStatement that lists only what is implemented": exactly DetectedIssue, read + search-type. */
class CapabilityStatementFactoryTest {

    private final CapabilityStatementFactory factory =
            new CapabilityStatementFactory(Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void listsExactlyOneResourceWithReadAndSearchType() {
        CapabilityStatement cs = factory.build();

        assertThat(cs.getRest()).hasSize(1);
        assertThat(cs.getRestFirstRep().getResource()).hasSize(1);
        CapabilityStatementRestResourceComponent resource = cs.getRestFirstRep().getResourceFirstRep();
        assertThat(resource.getType()).isEqualTo("DetectedIssue");
        assertThat(resource.getInteraction()).extracting(i -> i.getCode())
                .containsExactlyInAnyOrder(TypeRestfulInteraction.READ, TypeRestfulInteraction.SEARCHTYPE);
    }

    @Test
    void declaresExactlyTheTwoSearchParametersTheControllerSupports() {
        CapabilityStatement cs = factory.build();

        assertThat(cs.getRestFirstRep().getResourceFirstRep().getSearchParam())
                .extracting(sp -> sp.getName())
                .containsExactlyInAnyOrder("status", "identified");
    }

    @Test
    void declaresBothFhirJsonFormats() {
        CapabilityStatement cs = factory.build();

        assertThat(cs.getFormat()).extracting(f -> f.getValue())
                .containsExactlyInAnyOrder("json", "application/fhir+json");
    }
}
