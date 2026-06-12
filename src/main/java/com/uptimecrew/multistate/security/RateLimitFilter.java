package com.uptimecrew.multistate.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * Per-request rate-limit filter, registered AFTER BearerTokenAuthenticationFilter
 * so the resolved JWT principal is available when a token bucket is keyed.
 *
 * Task 1 only needs this as an injectable bean so SecurityConfig can wire it into
 * the chain — the body is a pass-through for now. Task 3 replaces this with the
 * Bucket4j-backed, per-principal token-bucket enforcement (429 on exhaustion).
 */
@Component
public final class RateLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // TODO(Task 3): look up the per-principal Bucket4j bucket and return 429
        //               when the bucket is exhausted. Pass-through until then.
        filterChain.doFilter(request, response);
    }
}
