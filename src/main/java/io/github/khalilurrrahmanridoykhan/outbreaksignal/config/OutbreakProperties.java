package io.github.khalilurrrahmanridoykhan.outbreaksignal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Application settings under the {@code outbreak.*} prefix, validated at startup. */
@Validated
@ConfigurationProperties(prefix = "outbreak")
public record OutbreakProperties(@Valid @DefaultValue Sync sync) {

    /**
     * Settings for pulling data from the source system.
     *
     * @param windowWeeks how many weeks of history each sync keeps in step with the source
     */
    public record Sync(@DefaultValue("104") @Min(8) @Max(520) int windowWeeks) {
    }
}
