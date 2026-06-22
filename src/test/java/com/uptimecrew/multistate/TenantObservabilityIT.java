package com.uptimecrew.multistate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uptimecrew.multistate.outbox.OutboxPublisher;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * W3 D5 Task 4 — trace-continuity proven in-process via {@link InMemorySpanExporter}.
 *
 * <p>Boots the full app against the five backing services the trace legs touch — Postgres,
 * Mongo, Redis, Kafka — plus a Jaeger all-in-one {@link GenericContainer}. Jaeger runs as a
 * GenericContainer because Testcontainers ships no jaegertracing module; it only absorbs any
 * OTLP traffic. It is NOT the source of truth — the {@code TestOtelConfig} below overrides the
 * {@link OpenTelemetry} bean with an SDK whose only exporter is the in-memory one (with a
 * {@link SimpleSpanProcessor}, NOT batch, so finished spans are readable immediately), and the
 * assertions read finished spans straight out of that exporter.
 *
 * <p>Deviations from the W3 D4 idealised reference, to match this codebase:
 * <ul>
 *   <li>The Kafka path is the W3 D3 transactional outbox (no synchronous POST→Kafka endpoint),
 *       so test 2 commits an outbox row and drives ONE {@link OutboxPublisher#publishPending()}
 *       under a known parent span. The background sweep is held dormant via
 *       {@code outbox.publisher.*-delay-ms} so the exporter contains only that one trace.</li>
 *   <li>{@code /api/**} is a secured resource server, so a test {@link JwtDecoder} grants the
 *       required scope/role; the GET targets an id absent from Mongo to force the Postgres
 *       (JDBC) read-model fallback and thus a JDBC child span.</li>
 *   <li>{@code TenantSummary} has four fields (no Confidence enum), and the service reads the
 *       raw {@link ChatResponse}, so the stub returns chatResponse() with token usage.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Keep the outbox sweep dormant so test 2 owns the only Kafka trace.
                "outbox.publisher.initial-delay-ms=600000",
                "outbox.publisher.fixed-delay-ms=600000"
        })
@Testcontainers
@ActiveProfiles("test")
class TenantObservabilityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // org.testcontainers.kafka.KafkaContainer (Apache image) — Boot 4's @ServiceConnection
    // supports this and ConfluentKafkaContainer, NOT the deprecated containers.KafkaContainer.
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    // No org.testcontainers:jaegertracing module exists — run all-in-one as a GenericContainer.
    // Present only to absorb OTLP traffic; the in-memory exporter is the source of truth.
    @Container
    static final GenericContainer<?> JAEGER =
            new GenericContainer<>(DockerImageName.parse("jaegertracing/all-in-one:1.62.0"))
                    .withEnv("COLLECTOR_OTLP_ENABLED", "true")
                    .withExposedPorts(16686, 4317, 4318);

    @BeforeAll
    static void applyPostgresSchema() throws Exception {
        // ddl-auto=none under the test profile; apply V1 (tables) + V3 (event_outbox) directly,
        // same as TenantGraphQlIT. No Postgres seed rows are needed: test 1 reads an absent id
        // (404 still emits the JDBC probe) and test 2 inserts its own outbox row.
        try (Connection conn = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
            stmt.execute(Files.readString(Path.of("db/V3__event_outbox.sql")));
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired InMemorySpanExporter spanExporter;
    @Autowired OpenTelemetry openTelemetry;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired TenantReadModelRepository readModelRepository;
    @Value("${local.server.port}") int port;

    @TestConfiguration
    static class TestOtelConfig {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        // Override the OpenTelemetry bean with an SDK whose only exporter is the in-memory one.
        // SimpleSpanProcessor (not Batch) flushes on span.end(), so getFinishedSpanItems() sees
        // a span the instant the request/operation returns — no test-wide sleeps. The OTel
        // starter's auto-instrumentations (servlet, JDBC, Kafka, Mongo) and the manual
        // llm.summarize tracer all resolve this @Primary bean, so every span lands here.
        @Bean
        @Primary
        OpenTelemetry openTelemetry(InMemorySpanExporter exporter) {
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
            // The W3C propagator is essential: the Kafka producer instrumentation injects the
            // traceparent header through it and the consumer extracts it, so the consumer span
            // joins the producer's trace (one trace id). A hand-built SDK defaults to a NO-OP
            // propagator — without this, producer and consumer land in separate traces.
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
        }

        // The summarize mutation must never reach Anthropic. LlmSummaryService reads the raw
        // ChatResponse, so stub .chatResponse() with schema-valid JSON text and non-null token
        // usage (17 in / 42 out) — exactly what test 3 asserts on the llm.summarize span.
        @Bean
        @Primary
        ChatClient.Builder stubChatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
            ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
            when(builder.build()).thenReturn(client);

            ChatResponse chatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
            when(chatResponse.getResult().getOutput().getText()).thenReturn(
                    "{\"primaryState\":\"CA\",\"totalAllocation\":100.0,"
                            + "\"stateCount\":3,\"complianceTier\":\"GREEN\"}");
            when(chatResponse.getMetadata().getUsage().getPromptTokens()).thenReturn(17);
            when(chatResponse.getMetadata().getUsage().getCompletionTokens()).thenReturn(42);
            when(client.prompt().user(anyString()).call().chatResponse()).thenReturn(chatResponse);
            return builder;
        }

        // /api/** is a secured resource server whose real issuer is unreachable in tests. This
        // decoder accepts any bearer token and grants the scope + role getById() requires, so
        // the request reaches the controller (and emits the server + JDBC spans test 1 checks).
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .claim("scope", "tenants.read")
                    .claim("roles", List.of("TENANT_READER"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    @BeforeEach
    void seedAndReset() {
        // Seed five read-model docs (seeded-id-1..5) so the summarize mutation resolves. Reset
        // the exporter AFTER seeding so the seed writes' Mongo spans don't bleed into a test.
        readModelRepository.deleteAll();
        List<TenantReadModel> docs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            docs.add(new TenantReadModel("seeded-id-" + i, "US-CA", Instant.now(), List.of()));
        }
        readModelRepository.saveAll(docs);
        spanExporter.reset();
    }

    @Test
    void httpRequest_emits_serverSpan_and_jdbcChildSpan() throws Exception {
        // 'no-such-tenant' is absent from Mongo, so findById falls through to the Postgres
        // (JDBC) read-model lookup — the 404 path still issues that SELECT. The bearer token
        // is accepted by the test JwtDecoder, so the request reaches the controller.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/tenants/no-such-tenant"))
                .header("Authorization", "Bearer test-token")
                .GET()
                .build();
        http.send(request, HttpResponse.BodyHandlers.ofString());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            SpanData server = spans.stream()
                    .filter(s -> s.getName().contains("/api/v1/tenants"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no HTTP server span for /api/v1/tenants"));

            boolean hasJdbcChild = spans.stream()
                    .anyMatch(s -> s.getTraceId().equals(server.getTraceId())
                            && (s.getName().toLowerCase().contains("select")
                                || s.getInstrumentationScopeInfo().getName().toLowerCase().contains("jdbc")));

            assertThat(hasJdbcChild)
                    .as("expected at least one JDBC child span sharing the HTTP server traceId")
                    .isTrue();
        });
    }

    @Test
    void kafkaWriteThrough_singleTraceId_endToEnd() throws Exception {
        // Commit an unpublished outbox row via raw JDBC (with an explicit ::jsonb cast), then
        // reset so this insert leaves no spans behind. NB: the JPA write path
        // (EventOutboxRepository.save) currently binds the String payload as bytea against the
        // jsonb column and fails — a separate latent bug; this test only needs the row to exist
        // so the poller can sweep it (the JPA *read* path works, as W3 D3 already relies on).
        try (Connection conn = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO multistate.event_outbox "
                    + "(id, aggregate_id, topic, payload, occurred_at) "
                    + "VALUES (gen_random_uuid(), 'trace-it-1', 'tenants.events', "
                    + "'{\"aggregateId\":\"trace-it-1\",\"allocations\":[]}'::jsonb, now())");
        }
        spanExporter.reset();

        // Drive ONE publish under a known parent span so the outbox SELECT, the Kafka producer
        // span, the consumer span (joined via the W3C header) and the Mongo write-through all
        // hang off a single trace. The background sweep is dormant, so nothing else is emitted.
        Tracer tracer = openTelemetry.getTracer("it.observability");
        Span root = tracer.spanBuilder("test.kafka-writethrough").startSpan();
        try (Scope ignored = root.makeCurrent()) {
            outboxPublisher.publishPending();
        } finally {
            root.end();
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<SpanData> all = spanExporter.getFinishedSpanItems();
            Set<String> traceIds = all.stream().map(SpanData::getTraceId).collect(Collectors.toSet());
            assertThat(traceIds)
                    .as("expected exactly one trace id across parent + JDBC + Kafka send + "
                            + "Kafka receive + Mongo, but saw: " + traceIds)
                    .hasSize(1);
            assertThat(all)
                    .as("expected >= 5 spans (parent + outbox SELECT + Kafka send + Kafka "
                            + "receive + Mongo find + Mongo save)")
                    .hasSizeGreaterThanOrEqualTo(5);
        });
    }

    @Test
    void llmSummarize_spanHasTokenAttributes() throws Exception {
        String body = "{\"query\":\"mutation { summarizeTenant(id: \\\"seeded-id-1\\\") "
                + "{ primaryState complianceTier } }\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.send(request, HttpResponse.BodyHandlers.ofString());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            SpanData llm = spanExporter.getFinishedSpanItems().stream()
                    .filter(s -> "llm.summarize".equals(s.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no llm.summarize span emitted"));

            assertThat(llm.getAttributes().get(AttributeKey.stringKey("llm.model")))
                    .as("llm.model").isNotBlank();
            assertThat(llm.getAttributes().get(AttributeKey.longKey("llm.tokens.in")))
                    .as("llm.tokens.in").isNotNull();
            assertThat(llm.getAttributes().get(AttributeKey.longKey("llm.tokens.out")))
                    .as("llm.tokens.out").isNotNull();
        });
    }
}
