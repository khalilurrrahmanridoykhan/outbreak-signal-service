package io.github.khalilurrrahmanridoykhan.outbreaksignal.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads roles from the JWT the same way a Keycloak access token carries
 * them: a nested {@code realm_access.roles} claim, not a flat one, so a
 * token from the optional local Keycloak (or any real Keycloak realm) works
 * without a translation layer. Each role becomes a {@code ROLE_*} authority
 * ({@code VIEWER} to {@code ROLE_VIEWER}), which is what {@code @PreAuthorize("hasRole('VIEWER')")}
 * expects.
 */
@Component
class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (Object role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return authorities;
    }
}
