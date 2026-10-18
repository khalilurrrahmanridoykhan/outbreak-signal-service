package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Plain resilience4j core objects, wired by hand rather than through
 * {@code resilience4j-spring-boot3}'s autoconfiguration — see the comment on
 * the dependency in {@code pom.xml} for why. {@link DhisWebClient} decorates
 * its calls with these two beans directly.
 */
@Configuration(proxyBeanMethods = false)
class DhisResilienceConfig {

    @Bean
    Retry dhisRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                // Only a DhisServerException (5xx or a connection failure) is
                // worth retrying; a DhisRequestException (4xx) would just
                // send the same bad request again. See the exception
                // hierarchy in this package.
                .retryOnException(DhisServerException.class::isInstance)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0))
                .build();
        return Retry.of("dhis2", config);
    }

    @Bean
    CircuitBreaker dhisCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(DhisServerException.class::isInstance)
                .build();
        return CircuitBreaker.of("dhis2", config);
    }
}
