package io.github.khalilurrrahmanridoykhan.outbreaksignal.detection;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires each {@link DetectionStrategy} as a bean, pulling its own narrow
 * configuration out of {@link OutbreakProperties}. The strategies themselves
 * take only that narrow config, not the whole properties tree, so a unit
 * test can construct one directly with no Spring context at all.
 */
@Configuration(proxyBeanMethods = false)
class DetectionStrategiesConfig {

    @Bean
    MovingAverageZStrategy movingAverageZStrategy(OutbreakProperties properties) {
        return new MovingAverageZStrategy(properties.detection().movingAverage());
    }

    @Bean
    EarsC1Strategy earsC1Strategy(OutbreakProperties properties) {
        return new EarsC1Strategy(properties.detection().ears());
    }

    @Bean
    EarsC2Strategy earsC2Strategy(OutbreakProperties properties) {
        return new EarsC2Strategy(properties.detection().ears());
    }

    @Bean
    EarsC3Strategy earsC3Strategy(EarsC2Strategy earsC2Strategy, OutbreakProperties properties) {
        return new EarsC3Strategy(earsC2Strategy, properties.detection().ears());
    }

    @Bean
    CusumStrategy cusumStrategy(OutbreakProperties properties) {
        return new CusumStrategy(properties.detection().cusum());
    }
}
