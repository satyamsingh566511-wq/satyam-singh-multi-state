package com.uptimecrew.multistate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.uptimecrew.multistate.entity.Tenant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test (IT suffix — runs against a real Postgres) for
 * {@link TenantRepository}, exercising a JPA round-trip and a derived finder.
 *
 * <p>{@code @DataJpaTest} slices the context to the persistence layer only — no
 * web layer, no full application context. By default it would replace the
 * DataSource with an in-memory H2; {@code @AutoConfigureTestDatabase(replace=NONE)}
 * keeps the real Testcontainers Postgres. {@code @ServiceConnection} wires the
 * container's JDBC URL / username / password straight into Spring, so no manual
 * {@code @DynamicPropertySource} is needed.
 *
 * <p>For a non-embedded datasource Hibernate's {@code ddl-auto} defaults to
 * {@code none}, so the schema is applied by hand in {@link #applySchema()} over a
 * raw JDBC connection. That runs OUTSIDE the per-test transaction, so the DDL
 * persists for the whole class while each {@code @Test} still rolls back its data.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TenantRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void applySchema() throws Exception {
        // createConnection("") retries until the database is reachable, absorbing
        // the brief startup race on VM-backed Docker runtimes where the host-side
        // port forward (PG.getJdbcUrl()) is established just after the in-container
        // "ready" log — see TenantQueryIT for the same idiom.
        try (Connection conn = PG.createConnection("");
             Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
        }
    }

    @Autowired
    TenantRepository repository;

    @Test
    void save_and_find_round_trip() {
        // Arrange — residency code left null (nullable, avoids the jurisdiction FK).
        var createdAt = Instant.parse("2026-01-15T00:00:00Z");
        var entity = new Tenant(
                "ten_round_trip", "Round Trip Co", "ext-round-trip", "ACTIVE", null, createdAt);

        // Act.
        repository.save(entity);
        Optional<Tenant> found = repository.findById("ten_round_trip");

        // Assert — the round-tripped row carries back exactly what was saved.
        assertThat(found).isPresent().get().satisfies(t -> {
            assertThat(t.getId()).isEqualTo("ten_round_trip");
            assertThat(t.getDisplayName()).isEqualTo("Round Trip Co");
            assertThat(t.getExternalRef()).isEqualTo("ext-round-trip");
            assertThat(t.getStatus()).isEqualTo("ACTIVE");
            assertThat(t.getResidencyJurisdictionCode()).isNull();
            assertThat(t.getCreatedAt()).isEqualTo(createdAt);
        });
    }

    @Test
    void derived_finder_returns_only_matching_rows() {
        // Arrange — two tenants differing only by status (the derived-finder field).
        var now = Instant.parse("2026-01-15T00:00:00Z");
        repository.save(new Tenant("ten_active", "Active Co", "ext-active", "ACTIVE", null, now));
        repository.save(new Tenant("ten_inactive", "Inactive Co", "ext-inactive", "INACTIVE", null, now));

        // Act + Assert — findByStatus returns exactly the one matching row.
        assertThat(repository.findByStatus("ACTIVE"))
                .extracting(Tenant::getId)
                .containsExactly("ten_active");
    }
}
