package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CorrelationIdFilter}, {@link RequestSizeLimitFilter} and
 * {@link RateLimitFilter} explicitly, all ordered ahead of
 * {@link SecurityFilterProperties#DEFAULT_FILTER_ORDER} (Spring Security's
 * own filter chain) rather than left as plain {@code @Component}-scanned
 * filters, which Boot would otherwise auto-register at
 * {@code Ordered.LOWEST_PRECEDENCE} — after Security, not before it. Order
 * among the three: a correlation id should exist before anything else can
 * log, an oversized body should be rejected before it consumes a rate-limit
 * token, and the rate limiter runs last of the three, still ahead of
 * authentication.
 */
@Configuration(proxyBeanMethods = false)
class FilterConfig {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 30);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(OutbreakProperties properties) {
        FilterRegistrationBean<RequestSizeLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestSizeLimitFilter(properties));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 20);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(OutbreakProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(properties));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }
}
