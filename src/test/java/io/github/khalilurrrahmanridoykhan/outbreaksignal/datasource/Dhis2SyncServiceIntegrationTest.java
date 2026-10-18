package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.Observation;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.ObservationRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.SeriesRepository;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the full wiring end to end — configuration, {@link DhisWebClient},
 * {@link Dhis2SyncService}, the JPA repositories, and a real PostgreSQL —
 * against a local WireMock, not the public demo. This is what proves the
 * plan's own requirement: a sync run three times leaves exactly one
 * {@code observation} row per (series, week), not three.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, Dhis2SyncServiceIntegrationTest.FixedClockConfig.class})
class Dhis2SyncServiceIntegrationTest {

    private static final String DATA_ELEMENT_UID = "vq2qO3eTrNi";
    private static final String ORG_UNIT_UID = "QwMiPiME3bA";

    private static WireMockServer wireMock;

    @DynamicPropertySource
    static void dhis2Properties(DynamicPropertyRegistry registry) {
        wireMock = new WireMockServer(0);
        wireMock.start();
        registry.add("outbreak.dhis2.base-url", wireMock::baseUrl);
        registry.add("outbreak.dhis2.username", () -> "admin");
        registry.add("outbreak.dhis2.password", () -> "district");
        registry.add("outbreak.dhis2.org-unit-level", () -> "3");
        registry.add("outbreak.dhis2.data-elements[0].uid", () -> DATA_ELEMENT_UID);
        registry.add("outbreak.dhis2.data-elements[0].name", () -> "IDSR Malaria");
        registry.add("outbreak.sync.window-weeks", () -> "8");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private Dhis2SyncService syncService;
    @Autowired
    private OrgUnitRepository orgUnits;
    @Autowired
    private SeriesRepository series;
    @Autowired
    private ObservationRepository observations;

    @BeforeEach
    void stubDhis2AndCleanTheDatabase() {
        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/api/organisationUnits.json")).willReturn(ok("""
                {"organisationUnits": [{"id": "%s", "name": "Kpanga Kabonde", "level": 3}]}
                """.formatted(ORG_UNIT_UID)).withHeader("Content-Type", "application/json")));
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(ok("""
                {"headers": [{"name": "dx"}, {"name": "ou"}, {"name": "pe"}, {"name": "value"}],
                 "rows": [["%s", "%s", "2026W41", "9"]]}
                """.formatted(DATA_ELEMENT_UID, ORG_UNIT_UID)).withHeader("Content-Type", "application/json")));
        observations.deleteAll();
        series.deleteAll();
        orgUnits.deleteAll();
    }

    @AfterEach
    void cleanUpAfter() {
        observations.deleteAll();
        series.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void runningTheSyncThreeTimesLeavesExactlyOneObservationRow() {
        SyncResult first = syncService.sync();
        SyncResult second = syncService.sync();
        SyncResult third = syncService.sync();

        assertThat(first.dataElements()).singleElement().satisfies(result -> {
            assertThat(result.succeeded()).isTrue();
            assertThat(result.observationsUpserted()).isEqualTo(1);
        });
        // The org unit is created once; the second and third runs find it already there.
        assertThat(second.orgUnitsCreated()).isZero();
        assertThat(third.orgUnitsCreated()).isZero();

        assertThat(orgUnits.findAll()).hasSize(1);
        assertThat(series.findAll()).hasSize(1);
        List<Observation> stored = observations.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getValue()).isEqualByComparingTo("9");

        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/api/organisationUnits.json")));
        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/api/analytics.json")));
    }

    @Test
    void aLaterSyncWithACorrectedValueUpdatesTheSameRowInstead() {
        syncService.sync();

        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(ok("""
                {"headers": [{"name": "dx"}, {"name": "ou"}, {"name": "pe"}, {"name": "value"}],
                 "rows": [["%s", "%s", "2026W41", "14"]]}
                """.formatted(DATA_ELEMENT_UID, ORG_UNIT_UID)).withHeader("Content-Type", "application/json")));
        syncService.sync();

        List<Observation> stored = observations.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getValue()).isEqualByComparingTo("14");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            // Any fixed instant works: the test only needs every sync run to
            // request the same window, not any particular one.
            return Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
