package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertService;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertStatus;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertSummary;
import java.time.LocalDate;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The FHIR read surface over the same alert data the REST API already
 * exposes — reusing {@link AlertService} rather than querying
 * {@code AlertRepository} directly, so this package never has to re-derive
 * its lazy-association-safe DTO mapping (see {@code DECISIONS.md} #29).
 */
@Service
class FhirAlertService {

    private final AlertService alertService;
    private final DetectedIssueMapper mapper;

    FhirAlertService(AlertService alertService, DetectedIssueMapper mapper) {
        this.alertService = alertService;
        this.mapper = mapper;
    }

    /** @throws io.github.khalilurrrahmanridoykhan.outbreaksignal.detection.AlertNotFoundException if no alert has this id */
    DetectedIssue findById(Long id) {
        AlertSummary alert = alertService.getDetail(id).alert();
        return mapper.map(alert);
    }

    /**
     * A FHIR searchset {@link Bundle}. {@code status}, if given, must be one
     * of the four codes {@link AlertStatusFhirMapping} actually uses; any
     * other syntactically valid {@code DetectedIssueStatus} code simply
     * matches nothing, rather than erroring. {@code identified} matches the
     * alerted week ({@code period_start}), not the literal {@code identified}
     * instant each resource reports — the nearest already-indexed,
     * queryable date on {@code Alert}; see {@code docs/fhir-mapping.md} for
     * why that simplification was an acceptable trade for this phase.
     */
    Bundle search(String status, LocalDate identified, Pageable pageable) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        Optional<AlertStatus> mappedStatus = status == null ? Optional.empty() : AlertStatusFhirMapping.toAlertStatus(status);
        if (status != null && mappedStatus.isEmpty()) {
            bundle.setTotal(0);
            return bundle;
        }

        Page<AlertSummary> page = alertService.search(mappedStatus.orElse(null), null, null, identified, identified, pageable);
        bundle.setTotal((int) page.getTotalElements());
        for (AlertSummary alert : page.getContent()) {
            bundle.addEntry().setResource(mapper.map(alert));
        }
        return bundle;
    }
}
