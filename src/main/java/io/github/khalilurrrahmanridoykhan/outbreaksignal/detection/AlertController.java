package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/api/v1/alerts")
class AlertController {

    private final AlertService alertService;

    AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    Page<AlertSummary> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) String algorithm,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "periodStart") Pageable pageable) {
        return alertService.search(status, orgUnitId, algorithm, from, to, pageable).map(AlertSummary::from);
    }

    @GetMapping("/{id}")
    AlertDetail get(@PathVariable Long id) {
        AlertSummary summary = AlertSummary.from(alertService.get(id));
        List<AlertEventSummary> history = alertService.history(id).stream().map(AlertEventSummary::from).toList();
        return new AlertDetail(summary, history);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    AlertSummary acknowledge(@PathVariable Long id, Principal principal) {
        return AlertSummary.from(alertService.acknowledge(id, principal.getName()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    AlertSummary resolve(@PathVariable Long id, Principal principal) {
        return AlertSummary.from(alertService.resolve(id, principal.getName()));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    AlertSummary dismiss(@PathVariable Long id, @Valid @RequestBody DismissRequest request, Principal principal) {
        return AlertSummary.from(alertService.dismiss(id, request.reason(), principal.getName()));
    }
}
