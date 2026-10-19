package io.github.khalilurrrahmanridoykhan.outbreaksignal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
public record OutbreakProperties(
        @Valid @DefaultValue Sync sync,
        @Valid Dhis2 dhis2,
        @Valid @DefaultValue Detection detection,
        @Valid @DefaultValue Security security) {

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

    /**
     * Parameters for the detection strategies. Each strategy's own parameters are
     * recorded on {@code detection_run} for every run, so an alert is always
     * reproducible from what is in this file plus the data it saw.
     */
    public record Detection(
            @Valid @DefaultValue MovingAverage movingAverage,
            @Valid @DefaultValue Ears ears,
            @Valid @DefaultValue Cusum cusum) {

        /**
         * @param baselineWeeks how many preceding weeks the mean and standard
         *     deviation are computed from
         * @param thresholdK how many standard deviations above the mean counts as
         *     an alert
         */
        public record MovingAverage(
                @DefaultValue("12") @Min(4) @Max(104) int baselineWeeks,
                @DefaultValue("3.0") @DecimalMin("0.5") double thresholdK) {
        }

        /**
         * Shared by C1, C2 and C3: the classic CDC EARS baseline of 7 preceding
         * weeks and a one-sided threshold of 3 standard deviations. C2 adds a
         * 2-week guard band before the baseline (fixed, not configurable, since
         * it is part of what distinguishes C2 from C1 rather than a tuning knob).
         *
         * @param baselineWeeks how many preceding weeks form the baseline
         * @param threshold how many standard deviations above the mean counts as
         *     an alert (also C3's cumulative-sum threshold)
         */
        public record Ears(
                @DefaultValue("7") @Min(3) @Max(52) int baselineWeeks,
                @DefaultValue("3.0") @DecimalMin("0.5") double threshold) {
        }

        /**
         * A one-sided CUSUM. The reference value and decision interval are
         * expressed in multiples of the baseline standard deviation, the usual
         * way to state them so they scale with the series.
         *
         * @param baselineWeeks how many of the series' earliest weeks establish
         *     the reference mean and standard deviation (the "in-control"
         *     period); the statistic is then accumulated over every week after it
         * @param referenceKSigma the allowed slack before a week contributes to
         *     the cumulative sum, in standard deviations
         * @param thresholdHSigma the cumulative sum level that triggers an alert,
         *     in standard deviations
         */
        public record Cusum(
                @DefaultValue("8") @Min(4) @Max(52) int baselineWeeks,
                @DefaultValue("0.5") @DecimalMin("0.0") double referenceKSigma,
                @DefaultValue("5.0") @DecimalMin("0.5") double thresholdHSigma) {
        }
    }

    /**
     * Where to validate incoming JWTs against. Defaults to the optional local
     * Keycloak's JWK endpoint (see {@code docker-compose.yml}'s {@code keycloak}
     * profile) rather than failing closed with no default, because building the
     * {@code JwtDecoder} from this URI never contacts it eagerly — see
     * {@code JwtDecoderConfig} for why that means the application still starts
     * with no issuer running. A real deployment overrides this with its own
     * identity provider's JWK set URI.
     *
     * @param jwkSetUri where to fetch the signing keys used to verify a JWT
     */
    public record Security(@DefaultValue("http://localhost:8081/realms/outbreak-signal/protocol/openid-connect/certs") @NotBlank String jwkSetUri) {
    }
}
