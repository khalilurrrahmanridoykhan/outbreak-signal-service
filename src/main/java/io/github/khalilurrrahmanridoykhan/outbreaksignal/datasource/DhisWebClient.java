package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.common.EpiWeek;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Talks to one DHIS2 instance over its Web API. Every call blocks (no
 * reactive pipeline above this class), is wrapped in a retry for transient
 * failures and a circuit breaker so a struggling DHIS2 instance is not
 * hammered, and translates every failure into {@link DhisRequestException}
 * or {@link DhisServerException} so the caller never has to know about
 * {@code WebClient}'s own exception types.
 */
@Component
class DhisWebClient implements DhisClient {

    private static final Logger log = LoggerFactory.getLogger(DhisWebClient.class);

    private final WebClient webClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    DhisWebClient(WebClient dhisHttpClient, Retry dhisRetry, CircuitBreaker dhisCircuitBreaker) {
        this.webClient = dhisHttpClient;
        this.retry = dhisRetry;
        this.circuitBreaker = dhisCircuitBreaker;
    }

    @Override
    public List<DhisOrgUnit> fetchOrgUnits(int level) {
        OrgUnitsResponse response = execute(() -> webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/organisationUnits.json")
                        .queryParam("filter", "level:eq:" + level)
                        .queryParam("fields", "id,name,level,parent[id]")
                        .queryParam("paging", "false")
                        .build())
                .retrieve()
                .bodyToMono(OrgUnitsResponse.class)
                .block());

        List<DhisOrgUnit> orgUnits = new ArrayList<>(response.organisationUnits().size());
        for (OrgUnitsResponse.Entry entry : response.organisationUnits()) {
            String parentUid = entry.parent() == null ? null : entry.parent().id();
            orgUnits.add(new DhisOrgUnit(entry.id(), entry.name(), entry.level(), parentUid));
        }
        log.info("Fetched {} org units at level {}", orgUnits.size(), level);
        return orgUnits;
    }

    @Override
    public List<DhisObservation> fetchWeeklyAnalytics(String dataElementUid, int level, List<EpiWeek> weeks) {
        if (weeks.isEmpty()) {
            return List.of();
        }
        String periods = weeks.stream().map(EpiWeek::toDhis2Period).collect(Collectors.joining(";"));

        AnalyticsResponse response = execute(() -> webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/analytics.json")
                        .queryParam("dimension", "dx:" + dataElementUid, "ou:LEVEL-" + level, "pe:" + periods)
                        .build())
                .retrieve()
                .bodyToMono(AnalyticsResponse.class)
                .block());

        int dxIndex = response.columnIndex("dx");
        int ouIndex = response.columnIndex("ou");
        int peIndex = response.columnIndex("pe");
        int valueIndex = response.columnIndex("value");

        List<DhisObservation> observations = new ArrayList<>(response.rows().size());
        for (List<String> row : response.rows()) {
            observations.add(new DhisObservation(
                    row.get(dxIndex),
                    row.get(ouIndex),
                    EpiWeek.parseDhis2Period(row.get(peIndex)),
                    new BigDecimal(row.get(valueIndex))));
        }
        log.info("Fetched {} weekly values for data element {} across {} requested weeks",
                observations.size(), dataElementUid, weeks.size());
        return observations;
    }

    private <T> T execute(Supplier<T> call) {
        Supplier<T> withExceptionTranslation = () -> {
            try {
                return call.get();
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().is5xxServerError()) {
                    throw new DhisServerException("DHIS2 returned " + e.getStatusCode(), e);
                }
                throw new DhisRequestException("DHIS2 returned " + e.getStatusCode(), e);
            } catch (WebClientRequestException e) {
                throw new DhisServerException("Could not reach DHIS2: " + e.getMessage(), e);
            }
        };
        Supplier<T> withRetry = Retry.decorateSupplier(retry, withExceptionTranslation);
        Supplier<T> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, withRetry);
        try {
            return withCircuitBreaker.get();
        } catch (CallNotPermittedException e) {
            throw new DhisServerException("DHIS2 circuit breaker is open", e);
        }
    }
}
