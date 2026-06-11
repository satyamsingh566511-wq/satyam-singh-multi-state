package com.uptimecrew.multistate.readmodel;

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

    public TenantReadModel() {}                     /* required by Spring Data Mongo */

    public TenantReadModel(String id,
                           String primaryState,
                           Instant capturedAt,
                           List<EmbeddedAllocation> allocations) {
        this.id = id;
        this.primaryState = primaryState;
        this.capturedAt = capturedAt;
        this.allocations = allocations != null ? allocations : new ArrayList<>();
    }

    public String getId()                          { return id; }
    public String getPrimaryState()                { return primaryState; }
    public Instant getCapturedAt()                 { return capturedAt; }
    public List<EmbeddedAllocation> getAllocations() { return allocations; }

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
