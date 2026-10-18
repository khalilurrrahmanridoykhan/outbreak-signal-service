package io.github.khalilurrrahmanridoykhan.outbreaksignal.datasource;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
class DhisWebClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    WebClient dhisHttpClient(OutbreakProperties properties) {
        OutbreakProperties.Dhis2 dhis2 = properties.dhis2();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(RESPONSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(dhis2.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeaders(headers -> headers.setBasicAuth(dhis2.username(), dhis2.password()))
                .build();
    }
}
