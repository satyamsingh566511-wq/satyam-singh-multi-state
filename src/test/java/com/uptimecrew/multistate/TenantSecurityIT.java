package com.uptimecrew.multistate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uptimecrew.multistate.model.WorkDay;
import com.uptimecrew.multistate.security.JwtAuthoritiesConverter;
import com.uptimecrew.multistate.service.AllocationService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Security + rate-limit integration test (W3 D1, Task 4). Reuses the W2 D5
 * three-container polyglot setup ({@link TenantPolyglotIT}); the JWT post-processor
 * from {@code spring-security-test} forges tokens through the resource-server
 * converter without needing a real IdP, so the controller's
 * {@code @PreAuthorize("hasAuthority('SCOPE_tenants.read') and hasRole('TENANT_READER')")}
 * gate is exercised against real authorities.
 *
 * <p>Postgres needs its schema applied once (the app runs {@code ddl-auto: none}
 * under the {@code test} profile), and {@code test-id} is seeded through the write
 * path before each test so {@code getById} resolves a present read model and
 * returns 200 rather than 404.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantSecurityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @BeforeAll
    static void applyPostgresSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    AllocationService service;

    /*
     * Seed "test-id" through the write-through path so getById finds a read model
     * and returns 200. summary() returns its stub unconditionally, so it does not
     * depend on this; getById does.
     */
    @BeforeEach
    void seedTestId() {
        service.allocate(
                "test-id",
                new BigDecimal("1000.00"),
                List.of(new WorkDay("day_sec1", "test-id", "US-CA", LocalDate.of(2026, 3, 2))),
                LocalDate.of(2026, 12, 31));
    }

    /*
     * The jwt() post-processor injects the Authentication directly and bypasses the
     * resource server's JwtAuthenticationConverter, so by default it derives only
     * scope-based authorities — the `roles` -> ROLE_* mapping never runs and
     * hasRole('TENANT_READER') would always fail. Feeding the production
     * JwtAuthoritiesConverter to .authorities(...) makes each forged token resolve
     * to the same SCOPE_* + ROLE_* authorities the live chain would produce.
     */
    @Test
    void getById_returns200_whenAuthenticatedWithScopeAndRole() throws Exception {
        mvc.perform(get("/api/tenants/test-id")
                .with(jwt().jwt(j -> j
                    .claim("scope", "tenants.read")
                    .claim("roles", List.of("TENANT_READER")))
                    .authorities(new JwtAuthoritiesConverter())))
           .andExpect(status().isOk());
    }

    @Test
    void getById_returns401_whenAnonymous() throws Exception {
        mvc.perform(get("/api/tenants/test-id"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returns403_whenJwtMissingRole() throws Exception {
        mvc.perform(get("/api/tenants/test-id")
                .with(jwt().jwt(j -> j
                    .claim("scope", "tenants.read")
                    .claim("roles", List.of()))
                    .authorities(new JwtAuthoritiesConverter())))
           .andExpect(status().isForbidden());
    }

    @Test
    void summary_returns429_after10Calls() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/tenants/test-id/summary")
                    .with(jwt().jwt(j -> j
                        .subject("rate-limit-user")
                        .claim("scope", "tenants.read")
                        .claim("roles", List.of("TENANT_READER")))
                        .authorities(new JwtAuthoritiesConverter())))
               .andExpect(status().isOk());
        }
        mvc.perform(get("/api/tenants/test-id/summary")
                .with(jwt().jwt(j -> j
                    .subject("rate-limit-user")
                    .claim("scope", "tenants.read")
                    .claim("roles", List.of("TENANT_READER")))
                    .authorities(new JwtAuthoritiesConverter())))
           .andExpect(status().isTooManyRequests())
           .andExpect(header().string("Retry-After", "60"));
    }
}
