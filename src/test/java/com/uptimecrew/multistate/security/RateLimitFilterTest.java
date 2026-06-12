package com.uptimecrew.multistate.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/*
 * Unit test for the per-principal token-bucket filter. The filter is a plain
 * servlet Filter, so it is exercised directly: a JwtAuthenticationToken is placed
 * on the SecurityContextHolder (standing in for the BearerTokenAuthenticationFilter
 * that runs before it in the real chain), then doFilter is driven against
 * MockHttpServletRequest/Response. No Spring context, IdP, or Docker is needed.
 *
 * Note: a fresh request/response/chain is built per invocation and verified
 * against those same instances — MockHttpServletRequest has no value equals(),
 * so Mockito argument matching is by identity here.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("token-for-" + subject)
                .header("alg", "none")
                .subject(subject)
                .claim("scope", "tenants.read")
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_tenants.read")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static MockHttpServletRequest request(String uri) {
        var req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        return req;
    }

    @Test
    void doFilter_eleventhSummaryCallSameSubject_returns429WithRetryAfter() throws Exception {
        authenticateAs("sub-over-limit");

        /* The first 10 calls fall within Bandwidth.classic(10, ...) and pass through. */
        for (int i = 1; i <= 10; i++) {
            var req = request("/api/tenants/any/summary");
            var res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(req, res, chain);

            verify(chain).doFilter(req, res);
            assertEquals(200, res.getStatus(), "call " + i + " should pass through with default 200");
        }

        /* The 11th call exhausts the bucket before the minute-boundary refill. */
        var req = request("/api/tenants/any/summary");
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertEquals(429, res.getStatus());
        assertEquals("60", res.getHeader("Retry-After"));
        assertEquals("application/json", res.getContentType());
        assertEquals("{\"error\":\"rate_limited\"}", res.getContentAsString());
    }

    @Test
    void doFilter_distinctSubjects_haveIndependentBuckets() throws Exception {
        /* Exhaust subject A's 10-token budget. */
        authenticateAs("sub-a");
        for (int i = 0; i < 10; i++) {
            filter.doFilter(request("/api/tenants/any/summary"), new MockHttpServletResponse(), mock(FilterChain.class));
        }
        var aRes = new MockHttpServletResponse();
        filter.doFilter(request("/api/tenants/any/summary"), aRes, mock(FilterChain.class));
        assertEquals(429, aRes.getStatus(), "subject A should be rate-limited after 10 calls");

        /* Subject B's bucket is untouched — its first call passes through. */
        SecurityContextHolder.clearContext();
        authenticateAs("sub-b");
        var bReq = request("/api/tenants/any/summary");
        var bRes = new MockHttpServletResponse();
        FilterChain bChain = mock(FilterChain.class);
        filter.doFilter(bReq, bRes, bChain);
        verify(bChain).doFilter(bReq, bRes);
        assertEquals(200, bRes.getStatus(), "subject B has its own budget");
    }

    @Test
    void doFilter_nonSummaryApiPath_passesThroughUnmetered() throws Exception {
        authenticateAs("sub-nonsummary");

        /* Well past the 10-token budget — but a path that does not end in /summary is never metered. */
        for (int i = 0; i < 20; i++) {
            var req = request("/api/tenants/any");
            var res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(req, res, chain);
            verify(chain).doFilter(req, res);
            assertEquals(200, res.getStatus());
        }
    }

    @Test
    void doFilter_nonApiPath_passesThroughWithoutTouchingSecurityContext() throws Exception {
        /* No authentication set: a non-/api path must short-circuit before the
         * principal lookup, proving it is not metered. */
        var req = request("/actuator/health");
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertEquals(200, res.getStatus());
    }

    @Test
    void doFilter_downstreamChainThrows_propagatesException() throws Exception {
        authenticateAs("sub-error");
        var req = request("/api/tenants/any/summary");
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("downstream boom")).when(chain).doFilter(req, res);

        ServletException thrown = assertThrows(ServletException.class,
                () -> filter.doFilter(req, res, chain));
        assertTrue(thrown.getMessage().contains("downstream boom"));
    }
}
