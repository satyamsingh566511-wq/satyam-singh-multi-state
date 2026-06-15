package com.uptimecrew.multistate.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/*
 * Spring Security 7 resource-server configuration.
 *
 * One @Bean SecurityFilterChain replaces the legacy WebSecurityConfigurerAdapter.
 * The chain runs BEFORE DispatcherServlet, so unauthenticated requests never
 * reach a controller — the default-deny contract Spring Security 7 enforces once
 * it is on the classpath.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)            /* (1) turns @PreAuthorize on for Task 2's method-level checks */
/* Not final: Spring CGLIB-proxies @Configuration classes, so the class and its @Bean method must stay non-final. */
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
            /*
             * (2) Stateless Bearer-only API; no session cookie means no CSRF
             *     surface to protect, so CSRF is safe to disable. If this app
             *     ever grows a cookie-based login, re-enable CSRF FIRST.
             */
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(jwt -> jwt
                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
            /*
             * (3) Place the rate-limit filter AFTER the bearer-token filter so the
             *     JWT principal is already resolved when the bucket is looked up.
             */
            .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /*
     * Maps both the standard `scope` claim (space-delimited) -> SCOPE_*
     * authorities AND a custom `roles` claim -> ROLE_* authorities. The
     * combination of the two is what the controller's @PreAuthorize SpEL checks.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
        return conv;
    }
}
