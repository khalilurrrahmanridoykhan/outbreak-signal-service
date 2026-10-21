package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

/** Read-only: every alert status already maps to a {@code DetectedIssue}; nothing here ever accepts one. */
@RestController
@RequestMapping("/fhir/DetectedIssue")
class DetectedIssueController {

    private final FhirAlertService fhirAlertService;

    DetectedIssueController(FhirAlertService fhirAlertService) {
        this.fhirAlertService = fhirAlertService;
    }

    @GetMapping(value = "/{id}", produces = {"application/fhir+json", "application/json"})
    DetectedIssue read(@PathVariable Long id) {
        return fhirAlertService.findById(id);
    }

    @GetMapping(produces = {"application/fhir+json", "application/json"})
    Bundle search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate identified,
            @PageableDefault(size = 20) Pageable pageable) {
        return fhirAlertService.search(status, identified, pageable);
    }
}
