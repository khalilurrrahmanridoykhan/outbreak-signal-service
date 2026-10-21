package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import java.time.Clock;
import java.util.Date;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementSoftwareComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.RestfulCapabilityMode;
import org.hl7.fhir.r4.model.CapabilityStatement.TypeRestfulInteraction;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code CapabilityStatement} fresh on every call — one {@code GET
 * /fhir/metadata}'s worth of object allocation is not worth caching, and a
 * fresh build can never drift from what {@link DetectedIssueController}
 * actually implements the way a hand-maintained static document could.
 * Lists only {@code DetectedIssue} {@code read}/{@code search-type}: the
 * plan's "honest {@code CapabilityStatement}" requirement.
 */
@Component
class CapabilityStatementFactory {

    private final Clock clock;

    CapabilityStatementFactory(Clock clock) {
        this.clock = clock;
    }

    CapabilityStatement build() {
        CapabilityStatement capabilityStatement = new CapabilityStatement();
        capabilityStatement.setStatus(PublicationStatus.ACTIVE);
        capabilityStatement.setDate(Date.from(clock.instant()));
        capabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        capabilityStatement.setSoftware(new CapabilityStatementSoftwareComponent().setName("Outbreak Signal Service"));
        capabilityStatement.setFhirVersion(FHIRVersion._4_0_1);
        capabilityStatement.addFormat("json");
        capabilityStatement.addFormat("application/fhir+json");

        CapabilityStatementRestComponent rest = capabilityStatement.addRest();
        rest.setMode(RestfulCapabilityMode.SERVER);

        CapabilityStatementRestResourceComponent detectedIssue = rest.addResource();
        detectedIssue.setType("DetectedIssue");
        detectedIssue.addInteraction().setCode(TypeRestfulInteraction.READ);
        detectedIssue.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
        detectedIssue.addSearchParam()
                .setName("status")
                .setType(SearchParamType.TOKEN)
                .setDocumentation("One of registered | preliminary | final | cancelled (see AlertStatusFhirMapping); "
                        + "any other DetectedIssueStatus code matches nothing. Not a standard R4 DetectedIssue search parameter.");
        detectedIssue.addSearchParam()
                .setName("identified")
                .setType(SearchParamType.DATE)
                .setDocumentation("Exact-day match against the alerted week (period_start), not the literal "
                        + "identified instant each resource reports. Range prefixes (ge, le, ...) are not supported.");

        return capabilityStatement;
    }
}
