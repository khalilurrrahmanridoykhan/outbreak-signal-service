package io.github.khalilurrrahmanridoykhan.outbreaksignal.security;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.config.OutbreakProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Built from a {@code jwk-set-uri} rather than letting Boot's own
 * auto-configuration build it from an {@code issuer-uri}: the issuer-uri
 * path performs OIDC discovery (an HTTP call to the issuer) while this bean
 * is being created, which would make the whole application fail to start
 * whenever the configured issuer is unreachable — including the default
 * {@code docker compose up}, which does not run the optional Keycloak
 * profile. Building from {@code jwk-set-uri} defers fetching the key set
 * until the first token actually needs validating, so the application
 * always starts; only an authenticated request needs the issuer reachable.
 */
@Configuration(proxyBeanMethods = false)
class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(OutbreakProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.security().jwkSetUri()).build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(JwtRoleConverter jwtRoleConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtRoleConverter);
        return converter;
    }
}
