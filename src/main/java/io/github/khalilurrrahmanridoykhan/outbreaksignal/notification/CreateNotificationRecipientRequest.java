package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /notification-recipients}. */
public record CreateNotificationRecipientRequest(
        @NotNull Long orgUnitId,
        @NotBlank @Email String email) {
}
