package com.uptimecrew.multistate.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.uptimecrew.multistate.clients.IdentityProfile;
import com.uptimecrew.multistate.clients.IdentityService;
import com.uptimecrew.multistate.config.TestWebMvcConfiguration;
import com.uptimecrew.multistate.security.JwtAuthoritiesConverter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Pattern reference for Task 4 (WireMock + OpenAPI assertion).
//
// WireMock listens on port 8090; application.yml's identity.base-url points
// the Feign client at it. We drive the breaker open by stubbing 500s, then
// assert subsequent calls hit the fallback (not WireMock).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestWebMvcConfiguration.class)
@ActiveProfiles("test")
class IdentityClientCircuitBreakerIT {

    @RegisterExtension
    static final WireMockExtension WM = WireMockExtension.newInstance()
            .options(wireMockConfig().port(8090))
            .build();

    @Autowired IdentityService identityService;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired MockMvc mvc;

    @BeforeEach
    void setUp() {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("identity");
        breaker.reset();
    }

    @AfterEach
    void tearDown() {
        WM.resetAll();
    }

    @Test
    void getProfile_returnsBody_whenIdentityIs200() {
        WM.stubFor(WireMock.get(urlEqualTo("/identity/u-1/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"u-1\",\"displayName\":\"Pat\",\"region\":\"us-east-1\"}")));

        IdentityProfile p = identityService.getProfile("u-1");
        IdentityProfile expected = new IdentityProfile("u-1", "Pat", "us-east-1");

        assertThat(p)
                .as("Actual profile: %s, Expected: %s", p, expected)
                .isEqualTo(expected);
    }

    @Test
    void circuitOpens_after_repeated_5xx() {
        WM.stubFor(WireMock.get(urlEqualTo("/identity/u-2/profile"))
                .willReturn(aResponse().withStatus(500)));

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("identity");
        for (int i = 0; i < 20 && breaker.getState() != CircuitBreaker.State.OPEN; i++) {
            try { identityService.getProfile("u-2"); } catch (Exception ignored) { }
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // While OPEN, further calls short-circuit to the fallback without
        // reaching WireMock; record the count and assert it does not grow.
        int callsBefore = WM.findAll(getRequestedFor(urlEqualTo("/identity/u-2/profile"))).size();
        IdentityProfile fallback = identityService.getProfile("u-2");
        int callsAfter = WM.findAll(getRequestedFor(urlEqualTo("/identity/u-2/profile"))).size();

        assertThat(callsAfter).isEqualTo(callsBefore);
        assertThat(fallback.displayName()).isEmpty();
    }

    @Test
    void summary_returns200_andIncludesProfileDisplayName() throws Exception {
        WM.stubFor(WireMock.get(urlEqualTo("/identity/u-3/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"u-3\",\"displayName\":\"Sam\",\"region\":\"us-east-1\"}")));

        mvc.perform(post("/api/v1/tenants/{id}/summary", "abc")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(jwt().jwt(j -> j
                                .subject("u-3")
                                .claim("scope", "tenants.read")
                                .claim("roles", List.of("TENANT_READER")))
                                .authorities(new JwtAuthoritiesConverter())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Sam"));
    }

    @Test
    void openApiDoc_exposesV1Path() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/tenants/{id}']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme").value("bearer"));
    }
}
