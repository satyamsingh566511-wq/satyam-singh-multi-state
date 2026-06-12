package com.uptimecrew.multistate.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/*
 * Single source of truth for turning a JWT into Spring Security authorities:
 * the standard space-delimited `scope` claim -> SCOPE_* (via the built-in
 * JwtGrantedAuthoritiesConverter) plus a custom `roles` claim -> ROLE_*. The
 * combination is exactly what the controller's @PreAuthorize SpEL checks.
 *
 * Extracted from SecurityConfig so the same mapping can be reused by tests: the
 * spring-security-test jwt() post-processor injects the Authentication directly
 * and bypasses the resource server's converter, so TenantSecurityIT feeds this
 * converter to jwt().authorities(...) to exercise the production role mapping
 * rather than the post-processor's scope-only default.
 */
public final class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    public JwtAuthoritiesConverter() {
        scopes.setAuthorityPrefix("SCOPE_");
        scopes.setAuthoritiesClaimName("scope");
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> scopeAuths = scopes.convert(jwt);
        List<String> roles = jwt.getClaimAsStringList("roles");
        Stream<GrantedAuthority> roleAuths = (roles == null ? Stream.<String>empty() : roles.stream())
            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r));
        return Stream.concat(scopeAuths.stream(), roleAuths).toList();
    }
}
