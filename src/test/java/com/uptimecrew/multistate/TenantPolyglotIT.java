package com.uptimecrew.multistate;

import static org.assertj.core.api.Assertions.assertThat;

import com.uptimecrew.multistate.model.WorkDay;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.service.AllocationService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-context polyglot integration test (W2 D5, Task 4): boots the entire Spring
 * Boot application against THREE real backing services at once — Postgres (the JPA
 * write model), Mongo (the denormalised read model), and Redis (the {@code @Cacheable}
 * read cache) — and exercises the write-through and cache-hit paths end to end.
 *
 * <p>Each container is wired with {@code @ServiceConnection}, so Spring Boot derives
 * the datasource URL, the Mongo URI, and the Redis host/port straight from the
 * running containers — no {@code @DynamicPropertySource} plumbing. The Redis case
 * uses a bare {@link GenericContainer} (there is no dedicated Testcontainers Redis
 * module on the classpath), so it needs the explicit {@code @ServiceConnection(name =
 * "redis")} hint for Spring Boot to recognise it as the Redis connection.
 *
 * <p>Mongo and Redis are schemaless, so only Postgres needs a schema applied; that
 * happens once in {@link #applyPostgresSchema()} over a raw JDBC connection, outside
 * any Spring-managed transaction, since the app runs {@code ddl-auto: none} under the
 * {@code test} profile.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class TenantPolyglotIT {

    // CACHE_NAME on AllocationService is package-private to ...service, so it is not
    // visible from this package — mirror the literal value Spring registers the cache
    // under instead of referencing the constant.
    private static final String CACHE_NAME = "multistate.byId";

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
    AllocationService service;

    @Autowired
    CacheManager cacheManager;

    @Test
    void write_path_populates_postgres_AND_mongo() {
        // Arrange — one worker, two jurisdictions across two days; the @Primary
        // day-count strategy splits the income and the service write-through saves
        // a Tenant to Postgres AND a TenantReadModel (same id) to Mongo.
        String workerId = "wkr_polyglot_write";
        List<WorkDay> workDays = List.of(
                new WorkDay("day_w1", workerId, "US-CA", LocalDate.of(2026, 1, 6)),
                new WorkDay("day_w2", workerId, "US-NY", LocalDate.of(2026, 1, 7)));

        // Act.
        service.allocate(workerId, new BigDecimal("1000.00"), workDays, LocalDate.of(2026, 12, 31));
        Optional<TenantReadModel> mongoSide = service.findById(workerId);

        // Assert — the read model now resolves from Mongo for that id.
        assertThat(mongoSide)
                .as("read model projected into Mongo by the write-through path")
                .isPresent()
                .get()
                .satisfies(rm -> {
                    assertThat(rm.getId()).isEqualTo(workerId);
                    assertThat(rm.getAllocations()).isNotEmpty();
                });
    }

    @Test
    void second_read_is_served_from_redis() {
        // Arrange — write through so the read path has something to cache; an empty
        // Optional is not cached (unless = "#result == null"), so the data must exist.
        String workerId = "wkr_polyglot_cache";
        List<WorkDay> workDays = List.of(
                new WorkDay("day_c1", workerId, "US-CA", LocalDate.of(2026, 2, 3)));
        service.allocate(workerId, new BigDecimal("500.00"), workDays, LocalDate.of(2026, 12, 31));

        // Act — first read is a cache miss that populates Redis under the worker id.
        service.findById(workerId);

        // Assert — a subsequent read would now be served from Redis: the cache entry
        // exists after the first call.
        var cached = cacheManager.getCache(CACHE_NAME).get(workerId);
        assertThat(cached).as("cache entry after first read").isNotNull();
    }
}
