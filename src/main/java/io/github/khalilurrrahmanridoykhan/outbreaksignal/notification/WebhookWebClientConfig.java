package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * No base URL, unlike {@code DhisWebClientConfig}'s client: every subscriber
 * has its own target URL, supplied per request by {@link WebhookDispatcher}.
 */
@Configuration(proxyBeanMethods = false)
class WebhookWebClientConfig {

    @Bean
    WebClient webhookHttpClient(OutbreakProperties properties) {
        long timeoutMs = properties.notification().webhook().requestTimeoutMs();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)
                .doOnConnected(connection -> connection.addHandlerLast(
                        new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
