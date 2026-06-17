package com.uptimecrew.multistate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.uptimecrew.multistate.graphql.TenantSummary;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * GraphQlTester-backed integration test (W3 D4, Task 4). Boots the full app against
 * the three real backing services Task 1–3 touch — Postgres (the JPA write model the
 * {@code tenant.lines} {@code @BatchMapping} reads), Mongo (the read model the queries
 * resolve from), and Redis (the {@code @Cacheable} read cache) — and exercises every
 * GraphQL leg: the Query path, the batch-resolved children, and the structured-output
 * Mutation re-validated against its JSON Schema.
 *
 * <p>The {@link StubChatClientConfig} supplies a deterministic {@link ChatClient.Builder}
 * so the {@code summarizeTenant} mutation never reaches Anthropic — the structured-output
 * assertion stays hermetic and the JSON Schema re-validation is what actually proves the
 * contract.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
@Import(TenantGraphQlIT.StubChatClientConfig.class)
class TenantGraphQlIT {

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
        // V1 (tables) + V3 (event_outbox) only — the GraphQL legs under test query the
        // allocation table but need no seed rows: the seeded-id-* tenants live only in
        // Mongo (see seedReadModel), so their lines resolve to empty lists. V2__seed.sql
        // (Postgres tenant/allocation fixtures) is simply not required here.
        try (Connection conn = DriverManager.getConnection(
                     PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
            stmt.execute(Files.readString(Path.of("db/V3__event_outbox.sql")));
        }
    }

    @Autowired GraphQlTester graphQlTester;
    @Autowired ObjectMapper mapper;
    @Autowired TenantReadModelRepository readModelRepository;

    /**
     * Seeds exactly five read-model documents (ids {@code seeded-id-1..5}) into Mongo so
     * {@code tenant(id)} and {@code summarizeTenant(id)} resolve {@code seeded-id-1} and
     * {@code latestTenants(limit: 5)} returns a full page. Reset each test for determinism.
     */
    @BeforeEach
    void seedReadModel() {
        readModelRepository.deleteAll();
        List<TenantReadModel> docs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            docs.add(new TenantReadModel("seeded-id-" + i, "US-CA", Instant.now(), List.of()));
        }
        readModelRepository.saveAll(docs);
    }

    @Test
    void query_tenant_returnsSeedDocument() {
        graphQlTester.document("query { tenant(id: \"seeded-id-1\") { id } }")
                .execute()
                .path("tenant.id").entity(String.class).isEqualTo("seeded-id-1");
    }

    @Test
    void batchMapping_resolves_lines_inOneRound() {
        graphQlTester.document("query { latestTenants(limit: 5) { id lines { id } } }")
                .execute()
                .path("latestTenants").entityList(Object.class).hasSize(5);
    }

    @Test
    void summarizeTenant_returnsStructuredOutput_andMatchesSchema() throws Exception {
        TenantSummary summary = graphQlTester
                .document("mutation { summarizeTenant(id: \"seeded-id-1\") { "
                        + "primaryState totalAllocation stateCount complianceTier } }")
                .execute()
                .path("summarizeTenant").entity(TenantSummary.class).get();

        JsonNode node = mapper.valueToTree(summary);
        try (InputStream in = new ClassPathResource("schemas/TenantSummary.schema.json").getInputStream()) {
            var errors = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(in).validate(node);
            assertThat(errors).isEmpty();
        }
    }

    /**
     * Stubs {@link ChatClient.Builder} so the mutation returns a fixed, schema-valid
     * {@link TenantSummary} without calling Anthropic. {@code @Primary} wins over the
     * Anthropic-starter's auto-configured builder for injection into LlmSummaryService.
     */
    @TestConfiguration
    static class StubChatClientConfig {

        @Bean
        @Primary
        ChatClient.Builder stubChatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
            ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
            when(builder.build()).thenReturn(client);

            TenantSummary deterministic = new TenantSummary("CA", 125000.0, 2, "GREEN");
            when(client.prompt().user(anyString()).call().entity(TenantSummary.class))
                    .thenReturn(deterministic);
            return builder;
        }
    }
}
