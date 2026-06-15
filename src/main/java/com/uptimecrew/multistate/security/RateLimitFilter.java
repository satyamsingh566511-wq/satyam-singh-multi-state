package com.uptimecrew.multistate.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * Per-request rate-limit filter, registered AFTER BearerTokenAuthenticationFilter
 * so the resolved JWT principal is available when a token bucket is keyed.
 *
 * Buckets are keyed by the JWT `sub` claim so each authenticated caller has its
 * own budget: 10 requests / minute with an intervally-refilled bandwidth (the
 * whole 10 tokens are restored in one step at the minute boundary, not drip-fed).
 *
 * Only /api/** requests whose path ends in /summary are metered — those are the
 * LLM-facing routes worth protecting; everything else is a straight pass-through.
 *
 * STORE CHOICE: an in-memory ConcurrentHashMap is adequate for this assignment.
 * In production this would be backed by bucket4j-redis (already on the classpath)
 * so the limit is shared across instances. Trade-off: a distributed bucket costs
 * one Redis round-trip per request but survives restarts and is shared fleet-wide;
 * the in-memory map is free per request but is lost on restart and is per-instance,
 * so N instances effectively grant N× the intended limit.
 */
@Component
public final class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> bucketsBySubject = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") || !uri.endsWith("/summary")) {
            filterChain.doFilter(request, response);
            return;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jat)) {
            /*
             * Filter-chain ordering guarantees the bearer-token filter has already
             * rejected anonymous /api/** calls, so reaching here without a JWT is
             * not expected — pass through rather than meter an absent principal.
             */
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt = jat.getToken();
        String subject = jwt.getSubject();

        Bucket bucket = bucketsBySubject.computeIfAbsent(subject, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); /* 429 Too Many Requests — no SC_ constant in jakarta.servlet */
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\"}");
        }
    }
}
