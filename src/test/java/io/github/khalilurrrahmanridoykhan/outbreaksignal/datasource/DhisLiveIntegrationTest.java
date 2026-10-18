package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.EpiWeek;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Hits the real public DHIS2 demo instance (read-only). Tagged {@code live}
 * and excluded by default (see the {@code maven-surefire-plugin} config in
 * {@code pom.xml}); run it deliberately with {@code ./mvnw test -Dgroups=live}.
 *
 * <p>Per {@code docs/dhis2-spike.md} section 4, the newest week or two on this
 * demo is often incomplete, and exactly how far the data reaches forward
 * moves unpredictably over time (the spike's own first finding of a
 * permanently frozen cutoff turned out to be wrong a month later). So this
 * asserts against a window of history already confirmed present — weeks
 * ending at {@code 2026W9}, known good since the spike — never against "this
 * week" or "the newest data," which could start failing for reasons having
 * nothing to do with a bug in this client.
 */
@Tag("live")
class DhisLiveIntegrationTest {

    // The resolved host from the spike; see docs/dhis2-spike.md section 1 for
    // why this is configuration, not something resolved automatically, and
    // why it will need updating when the demo moves to its next patch version.
    private static final String BASE_URL = "https://play.im.dhis2.org/stable-2-40-12";
    private static final String MALARIA_UID = "vq2qO3eTrNi";

    private final DhisWebClient client = new DhisWebClient(
            WebClient.builder()
                    .baseUrl(BASE_URL)
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                    .defaultHeaders(headers -> headers.setBasicAuth("admin", "district"))
                    .build(),
            Retry.ofDefaults("live-test"),
            CircuitBreaker.ofDefaults("live-test"));

    @Test
    void fetchesTheSierraLeoneDistrictsAtLevel3() {
        List<DhisOrgUnit> districts = client.fetchOrgUnits(3);

        // The spike found 152 districts in the hierarchy; a loose lower bound
        // tolerates the demo's content changing slightly without this test
        // becoming a brittle re-assertion of an exact count.
        assertThat(districts.size()).isGreaterThanOrEqualTo(100);
        assertThat(districts).allSatisfy(district -> assertThat(district.level()).isEqualTo(3));
    }

    @Test
    void theKnownHistoricalMalariaWindowIsStillThere() {
        List<EpiWeek> weeks = EpiWeek.parseDhis2Period("2026W9").lastWeeksEndingHere(10);

        List<DhisObservation> observations = client.fetchWeeklyAnalytics(MALARIA_UID, 3, weeks);

        assertThat(observations).isNotEmpty();
        assertThat(observations).allSatisfy(observation -> {
            assertThat(observation.dataElementUid()).isEqualTo(MALARIA_UID);
            assertThat(observation.value().signum()).isGreaterThanOrEqualTo(0);
        });
    }
}
