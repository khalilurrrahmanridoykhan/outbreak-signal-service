package io.github.khalilurrrahmanridoykhan.outbreaksignal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void readsRolesFromKeycloaksNestedRealmAccessClaim() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("ANALYST", "VIEWER"))));

        List<String> authorities = converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_ANALYST", "ROLE_VIEWER");
    }

    @Test
    void aTokenWithNoRealmAccessClaimHasNoAuthorities() {
        Jwt jwt = jwt(Map.of());

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void aRealmAccessClaimWithNoRolesHasNoAuthorities() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("other", "value")));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
