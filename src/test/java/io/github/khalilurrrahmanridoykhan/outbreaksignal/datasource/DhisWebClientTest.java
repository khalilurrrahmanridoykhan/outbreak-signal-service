package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.EpiWeek;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Exercises {@link DhisWebClient} against a local WireMock stub, not the real
 * DHIS2 demo. The success fixtures below are shaped exactly like the real
 * responses captured in {@code docs/dhis2-spike.md}, so the test suite stays
 * representative without depending on that instance being reachable.
 */
class DhisWebClientTest {

    private WireMockServer wireMock;
    private DhisWebClient client;

    @BeforeEach
    void startWireMockAndClient() {
        wireMock = new WireMockServer(0);
        wireMock.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(wireMock.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .defaultHeaders(headers -> headers.setBasicAuth("admin", "district"))
                .build();
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(3)
                .retryOnException(DhisServerException.class::isInstance)
                .intervalFunction(IntervalFunction.of(Duration.ofMillis(1)))
                .build());
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .failureRateThreshold(90.0f)
                .build());
        client = new DhisWebClient(webClient, retry, circuitBreaker);
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void fetchesOrgUnitsAtTheConfiguredLevel() {
        wireMock.stubFor(get(urlPathEqualTo("/api/organisationUnits.json"))
                .withQueryParam("filter", WireMock.equalTo("level:eq:3"))
                .willReturn(ok("""
                        {"organisationUnits": [
                          {"id": "QwMiPiME3bA", "name": "Kpanga Kabonde", "level": 3,
                           "parent": {"id": "O6uvpzGd5pu"}},
                          {"id": "jUb8gELQApl", "name": "Sierra Leone", "level": 1}
                        ]}
                        """).withHeader("Content-Type", "application/json")));

        List<DhisOrgUnit> orgUnits = client.fetchOrgUnits(3);

        assertThat(orgUnits).containsExactlyInAnyOrder(
                new DhisOrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, "O6uvpzGd5pu"),
                new DhisOrgUnit("jUb8gELQApl", "Sierra Leone", 1, null));
    }

    @Test
    void fetchesWeeklyAnalyticsShapedLikeTheRealDemoResponse() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(ok("""
                {"headers": [{"name": "dx"}, {"name": "ou"}, {"name": "pe"}, {"name": "value"}],
                 "rows": [
                   ["vq2qO3eTrNi", "QwMiPiME3bA", "2026W24", "9"],
                   ["vq2qO3eTrNi", "eV4cuxniZgP", "2026W10", "7"]
                 ]}
                """).withHeader("Content-Type", "application/json")));

        List<DhisObservation> observations = client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24")));

        assertThat(observations).containsExactlyInAnyOrder(
                new DhisObservation("vq2qO3eTrNi", "QwMiPiME3bA", EpiWeek.parseDhis2Period("2026W24"), new BigDecimal("9")),
                new DhisObservation("vq2qO3eTrNi", "eV4cuxniZgP", EpiWeek.parseDhis2Period("2026W10"), new BigDecimal("7")));
    }

    @Test
    void readsColumnsByHeaderNameNotByAssumedPosition() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(ok("""
                {"headers": [{"name": "pe"}, {"name": "value"}, {"name": "ou"}, {"name": "dx"}],
                 "rows": [["2026W24", "9", "QwMiPiME3bA", "vq2qO3eTrNi"]]}
                """).withHeader("Content-Type", "application/json")));

        List<DhisObservation> observations = client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24")));

        assertThat(observations).containsExactly(
                new DhisObservation("vq2qO3eTrNi", "QwMiPiME3bA", EpiWeek.parseDhis2Period("2026W24"), new BigDecimal("9")));
    }

    @Test
    void anEmptyResultIsNotAnError() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(ok("""
                {"headers": [{"name": "dx"}, {"name": "ou"}, {"name": "pe"}, {"name": "value"}], "rows": []}
                """).withHeader("Content-Type", "application/json")));

        List<DhisObservation> observations = client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24")));

        assertThat(observations).isEmpty();
    }

    @Test
    void anUnauthorizedResponseIsNotRetriedAndFailsWithARequestException() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(unauthorized()));

        assertThatThrownBy(() -> client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24"))))
                .isInstanceOf(DhisRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/analytics.json")));
    }

    @Test
    void aBadRequestIsNotRetried() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(badRequest()));

        assertThatThrownBy(() -> client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24"))))
                .isInstanceOf(DhisRequestException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/analytics.json")));
    }

    @Test
    void aServerErrorIsRetriedAndThenSucceeds() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json"))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json"))
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(ok("""
                        {"headers": [{"name": "dx"}, {"name": "ou"}, {"name": "pe"}, {"name": "value"}],
                         "rows": [["vq2qO3eTrNi", "QwMiPiME3bA", "2026W24", "9"]]}
                        """).withHeader("Content-Type", "application/json")));

        List<DhisObservation> observations = client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24")));

        assertThat(observations).hasSize(1);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/api/analytics.json")));
    }

    @Test
    void exhaustingRetriesOnRepeatedServerErrorsFailsWithAServerException() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24"))))
                .isInstanceOf(DhisServerException.class);

        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/api/analytics.json")));
    }

    @Test
    void malformedJsonFailsClearlyInsteadOfSilently() {
        wireMock.stubFor(get(urlPathEqualTo("/api/analytics.json"))
                .willReturn(ok("{ this is not valid json").withHeader("Content-Type", "application/json")));

        assertThatThrownBy(() -> client.fetchWeeklyAnalytics(
                "vq2qO3eTrNi", 3, List.of(EpiWeek.parseDhis2Period("2026W24"))))
                .isInstanceOf(RuntimeException.class);

        // A decoding failure is a response-shape bug, not a transient
        // network blip: retrying the same malformed response is pointless.
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/analytics.json")));
    }

    @Test
    void aPaginatedOrgUnitResponseWithNoNextPageIsReadInOneCall() {
        // organisationUnits.json is requested with paging=false, so DHIS2
        // never actually paginates it; this confirms that query parameter is
        // sent and a plain (non-paged) body is read correctly either way.
        wireMock.stubFor(get(urlPathEqualTo("/api/organisationUnits.json"))
                .withQueryParam("paging", WireMock.equalTo("false"))
                .willReturn(ok("""
                        {"organisationUnits": [{"id": "QwMiPiME3bA", "name": "Kpanga Kabonde", "level": 3}]}
                        """).withHeader("Content-Type", "application/json")));

        List<DhisOrgUnit> orgUnits = client.fetchOrgUnits(3);

        assertThat(orgUnits).containsExactly(new DhisOrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
    }
}
