package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Triggers detection on demand. Admin-only — see {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/v1")
class DetectionController {

    private final DetectionService detectionService;

    DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping("/detection/run")
    @PreAuthorize("hasRole('ADMIN')")
    DetectionSummary triggerDetection() {
        return detectionService.runDetection();
    }
}
