package io.github.khalilurrrahmanridoykhan.outbreaksignal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Application settings under the {@code outbreak.*} prefix, validated at startup. */
@Validated
@ConfigurationProperties(prefix = "outbreak")
public record OutbreakProperties(@Valid @DefaultValue Sync sync, @Valid Dhis2 dhis2) {

    /**
     * Settings for pulling data from the source system.
     *
     * @param windowWeeks how many weeks of history each sync keeps in step with the source
     * @param cron when the scheduled sync runs (default: 03:00 UTC daily, a time no
     *     surveillance system's own reporting deadline is likely to land on)
     */
    public record Sync(
            @DefaultValue("104") @Min(8) @Max(520) int windowWeeks,
            @DefaultValue("0 0 3 * * *") @NotBlank String cron) {
    }

    /**
     * Where the DHIS2 instance is and what to pull from it.
     *
     * <p>Defaults point at the public DHIS2 demo (see {@code docs/dhis2-spike.md}
     * for why that base URL is the resolved host, not the {@code play.dhis2.org}
     * alias, and why it will occasionally need updating). They are deliberately
     * not secret and exist so a fresh clone can sync real data without any setup;
     * a real deployment overrides every one of them with its own instance,
     * credentials and data elements.
     *
     * @param baseUrl the DHIS2 instance's base URL, no trailing slash
     * @param username basic-auth username
     * @param password basic-auth password
     * @param orgUnitLevel which level of the org-unit hierarchy to sync as this
     *     project's districts (3 is "district" on the demo; see the spike)
     * @param dataElements the data elements to pull as series, one row of
     *     {@code observation} per (org unit, week) for each
     */
    public record Dhis2(
            @NotBlank String baseUrl,
            @NotBlank String username,
            @NotBlank String password,
            @DefaultValue("3") @Min(1) @Max(10) int orgUnitLevel,
            @NotEmpty List<@Valid DataElement> dataElements) {

        public record DataElement(@NotBlank String uid, @NotBlank String name) {
        }
    }
}
