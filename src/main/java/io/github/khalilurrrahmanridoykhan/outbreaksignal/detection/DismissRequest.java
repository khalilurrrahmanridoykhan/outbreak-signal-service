package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import jakarta.validation.constraints.NotBlank;

/** The body of {@code POST /alerts/{id}/dismiss}. A reason is required — see {@code AlertService.dismiss}. */
public record DismissRequest(@NotBlank String reason) {
}
