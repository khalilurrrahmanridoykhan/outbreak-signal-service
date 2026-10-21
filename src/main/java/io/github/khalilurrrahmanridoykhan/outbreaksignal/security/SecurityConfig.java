package io.github.khalilurrrahmanridoykhan.outbreaksignal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every endpoint under {@code /api/v1/**} requires a valid JWT; who can call
 * which one beyond that is enforced per-method with {@code @PreAuthorize}
 * (see {@link JwtRoleConverter} for where the roles it checks come from).
 * Health and OpenAPI stay open, since neither exposes data and a load
 * balancer needs the health endpoint reachable with no token.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // A CapabilityStatement is meant to be publicly discoverable —
                        // it describes what this server can do, not alert data itself,
                        // the same reasoning as the two matchers above.
                        .requestMatchers("/fhir/metadata").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                // A stateless JSON API with no cookie-based session has nothing for
                // CSRF to protect: a browser never attaches a bearer token to a
                // cross-site request the way it does a cookie, so there is no
                // ambient credential for CSRF to ride on. CodeQL's generic rule
                // flags any disabled CSRF protection regardless of this context;
                // see Spring Security's own reference docs on stateless APIs.
                // codeql[java/spring-disabled-csrf-protection]
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
