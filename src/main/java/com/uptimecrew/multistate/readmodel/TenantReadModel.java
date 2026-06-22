package com.uptimecrew.multistate.readmodel;

import com.uptimecrew.multistate.consumer.AllocationCreatedEvent;
import com.uptimecrew.multistate.model.IncomeAllocation;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Mongo read model for a tenant (W2 D5).
 *
 * This document EMBEDS the allocations that the W2 D4 JPA {@code Tenant} carries
 * as a LAZY {@code @OneToMany}. The point: ONE Mongo round-trip returns the whole
 * tree; the JPA side would need a JOIN FETCH to avoid N+1.
 *
 * The {@code id} is the SAME application-generated id as the JPA {@code Tenant},
 * so a Mongo lookup and a Postgres lookup return the same logical row.
 *
 * Implements {@link Serializable} (along with every nested class) so the
 * RedisCacheManager can serialise it — without it the first {@code @Cacheable}
 * miss explodes with a SerializationException at runtime.
 */
@Document(collection = "tenants")
public class TenantReadModel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Indexed                                        /* secondary index — index-backed lookup by state */
    private String primaryState;

    private Instant capturedAt;

    private List<EmbeddedAllocation> allocations = new ArrayList<>();   /* embedded, NOT a foreign reference */

    @Indexed                                        /* secondary index — index-backed lookup by tag */
    private List<String> tags = new ArrayList<>();  /* persisted free-form labels; never null */

    public TenantReadModel() {}                     /* required by Spring Data Mongo */

    /*
     * Backward-compatible 4-arg constructor: KEEPS its exact signature and
     * behaviour for the many existing callers/tests that depend on it. Tags
     * default to an empty list — never null.
     */
    public TenantReadModel(String id,
                           String primaryState,
                           Instant capturedAt,
                           List<EmbeddedAllocation> allocations) {
        this(id, primaryState, capturedAt, allocations, List.of());
    }

    /* 5-arg constructor for callers that supply tags. */
    public TenantReadModel(String id,
                           String primaryState,
                           Instant capturedAt,
                           List<EmbeddedAllocation> allocations,
                           List<String> tags) {
        this.id = id;
        this.primaryState = primaryState;
        this.capturedAt = capturedAt;
        this.allocations = allocations != null ? allocations : new ArrayList<>();
        // Defensive copy: this read model is cached and Redis-serialized, and CLAUDE.md
        // prizes immutable value types — don't let a caller's mutable list become our backing
        // store (which could desync the cache and corrupt tag lookups).
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getId()                          { return id; }
    public String getPrimaryState()                { return primaryState; }
    public Instant getCapturedAt()                 { return capturedAt; }
    public List<EmbeddedAllocation> getAllocations() { return allocations; }

    /* Non-null [String!]! GraphQL field — return an unmodifiable view, never null. */
    public List<String> getTags()                  { return tags != null ? List.copyOf(tags) : List.of(); }

    public void applyEvent(AllocationCreatedEvent event) {
        Instant now = Instant.now();
        for (IncomeAllocation allocation : event.allocations()) {
            EmbeddedAllocation incoming = new EmbeddedAllocation(
                    allocation.jurisdictionCode(),
                    allocation.amount(),
                    allocation.allocatedFor(),
                    now);
            boolean found = false;
            for (int i = 0; i < allocations.size(); i++) {
                EmbeddedAllocation existing = allocations.get(i);
                if (existing.getJurisdictionCode().equals(allocation.jurisdictionCode())
                        && existing.getAllocatedFor().equals(allocation.allocatedFor())) {
                    allocations.set(i, incoming);
                    found = true;
                    break;
                }
            }
            if (!found) {
                allocations.add(incoming);
            }
        }
        updatePrimaryState();
        this.capturedAt = now;
    }

    private void updatePrimaryState() {
        if (allocations.isEmpty()) {
            return;
        }
        primaryState = allocations.stream()
                .max((a, b) -> a.getAmount().compareTo(b.getAmount()))
                .map(EmbeddedAllocation::getJurisdictionCode)
                .orElse(primaryState);
    }

    /**
     * Denormalised copy of the JPA {@code Allocation} child — inlined into the
     * document instead of living in its own collection. Serializable so the
     * enclosing document round-trips through the cache.
     */
    public static class EmbeddedAllocation implements Serializable {

        private static final long serialVersionUID = 1L;

        private String jurisdictionCode;
        private BigDecimal amount;
        private LocalDate allocatedFor;
        private Instant createdAt;

        public EmbeddedAllocation() {}              /* required by Spring Data Mongo */

        public EmbeddedAllocation(String jurisdictionCode,
                                  BigDecimal amount,
                                  LocalDate allocatedFor,
                                  Instant createdAt) {
            this.jurisdictionCode = jurisdictionCode;
            this.amount = amount;
            this.allocatedFor = allocatedFor;
            this.createdAt = createdAt;
        }

        public String getJurisdictionCode() { return jurisdictionCode; }
        public BigDecimal getAmount()       { return amount; }
        public LocalDate getAllocatedFor()  { return allocatedFor; }
        public Instant getCreatedAt()       { return createdAt; }
    }
}
