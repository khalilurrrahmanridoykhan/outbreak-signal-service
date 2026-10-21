package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FhirMetadataController {

    private final CapabilityStatementFactory capabilityStatementFactory;

    FhirMetadataController(CapabilityStatementFactory capabilityStatementFactory) {
        this.capabilityStatementFactory = capabilityStatementFactory;
    }

    @GetMapping(value = "/fhir/metadata", produces = {"application/fhir+json", "application/json"})
    CapabilityStatement metadata() {
        return capabilityStatementFactory.build();
    }
}
