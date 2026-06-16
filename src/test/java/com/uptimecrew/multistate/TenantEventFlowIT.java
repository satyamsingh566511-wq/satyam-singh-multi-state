package com.uptimecrew.multistate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.outbox.EventOutboxRepository;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import com.uptimecrew.multistate.service.AllocationService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TenantEventFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.6.0");

    @BeforeAll
    static void applyPostgresSchema() throws Exception {
        // Retry mechanism to wait for PostgreSQL to be ready
        int attempts = 0;
        Exception lastException = null;
        while (attempts < 30) {
            try {
                Thread.sleep(500);
                try (Connection conn = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                     Statement stmt = conn.createStatement()) {
                    stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));
                    stmt.execute(Files.readString(Path.of("db/V2__seed.sql")));
                    stmt.execute(Files.readString(Path.of("db/V3__event_outbox.sql")));
                    return;
                }
            } catch (Exception ex) {
                lastException = ex;
                attempts++;
            }
        }
        throw new RuntimeException("Failed to apply PostgreSQL schema after 30 attempts", lastException);
    }

    @Autowired AllocationService service;
    @Autowired EventOutboxRepository outboxRepository;
    @Autowired TenantReadModelRepository readModelRepository;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper mapper;

    @Test
    void write_publishes_to_kafka_via_outbox() throws Exception {
        String aggregateId = "agg-" + UUID.randomUUID();
        LocalDate allocatedFor = LocalDate.now();

        service.allocate(
                aggregateId,
                new BigDecimal("1000.00"),
                List.of(),
                allocatedFor);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                        outboxRepository.findAll())
                .anyMatch(r -> r.getAggregateId().equals(aggregateId) && r.getPublishedAt() != null));

        CountDownLatch latch = new CountDownLatch(1);
        try (KafkaConsumer<String, String> probe = newProbe("probe-1", "tenants.events")) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                ConsumerRecord<String, String> rec = pollOne(probe);
                assertThat(rec).isNotNull();
                assertThat(rec.key()).isEqualTo(aggregateId);
                latch.countDown();
            });
        }
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void consumer_updates_mongo_read_model() throws Exception {
        String aggregateId = "agg-" + UUID.randomUUID();
        LocalDate allocatedFor = LocalDate.now();

        IncomeAllocation allocation = new IncomeAllocation(
                "alloc-" + UUID.randomUUID(),
                aggregateId,
                "CA",
                new BigDecimal("500.00"),
                allocatedFor);

        String payload = mapper.writeValueAsString(List.of(allocation));
        kafkaTemplate.send("tenants.events", aggregateId, payload).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> assertThat(readModelRepository.findById(aggregateId)).isPresent());
    }

    @Test
    void poison_pill_routes_to_dlt_after_retries() throws Exception {
        String aggregateId = "agg-" + UUID.randomUUID();
        kafkaTemplate
                .send("tenants.events", aggregateId, "{not valid json")
                .get(5, TimeUnit.SECONDS);

        try (KafkaConsumer<String, String> dlt = newProbe("dlt-probe", "tenants.events.DLT")) {
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(pollOne(dlt)).isNotNull());
        }
    }

    private KafkaConsumer<String, String> newProbe(String groupId, String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> c = new KafkaConsumer<>(props);
        c.subscribe(List.of(topic));
        return c;
    }

    private ConsumerRecord<String, String> pollOne(KafkaConsumer<String, String> c) {
        var records = c.poll(Duration.ofMillis(500));
        return records.isEmpty() ? null : records.iterator().next();
    }
}
