package com.uptimecrew.multistate.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_outbox", schema = "multistate")
public final class EventOutboxEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "topic", nullable = false, updatable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected EventOutboxEntity() { /* JPA only */ }

    public EventOutboxEntity(String aggregateId, String topic, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateId = java.util.Objects.requireNonNull(aggregateId, "aggregateId");
        this.topic = java.util.Objects.requireNonNull(topic, "topic");
        this.payload = java.util.Objects.requireNonNull(payload, "payload");
        this.occurredAt = Instant.now();
    }

    public UUID getId()                  { return id; }
    public String getAggregateId()       { return aggregateId; }
    public String getTopic()             { return topic; }
    public String getPayload()           { return payload; }
    public Instant getOccurredAt()       { return occurredAt; }
    public Instant getPublishedAt()      { return publishedAt; }

    public void markPublished(Instant when) {
        this.publishedAt = java.util.Objects.requireNonNull(when, "when");
    }
}
