package io.github.khalilurrrahmanridoykhan.outbreaksignal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One {@link Clock} bean so anything that needs "now" (the sync's window of weeks, chiefly) can be given a fixed one in tests. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
