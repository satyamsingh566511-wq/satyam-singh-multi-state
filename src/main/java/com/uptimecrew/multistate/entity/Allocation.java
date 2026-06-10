package com.uptimecrew.multistate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// Child entity — computed income allocation per tenant per jurisdiction per
// period. Persisted form of the Week 1 IncomeAllocation record. No @OneToMany
// here: it only carries the @ManyToOne back-reference to its owning Tenant.
@Entity
@Table(schema = "multistate", name = "allocation")
public class Allocation {

    @Id
    @Column(length = 64)
    private String id;                              // TEXT id, application-generated, never @GeneratedValue

    @ManyToOne(fetch = FetchType.LAZY)              // LAZY to dodge N+1
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "jurisdiction_code", nullable = false)
    private String jurisdictionCode;

    // money is NUMERIC(12,2) — BigDecimal, never double/float.
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocated_for", nullable = false)
    private LocalDate allocatedFor;                 // calendar period — LocalDate, not a timestamp

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Allocation() {}                       // required by JPA

    public Allocation(String id,
                      Tenant tenant,
                      String jurisdictionCode,
                      BigDecimal amount,
                      LocalDate allocatedFor,
                      Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        this.jurisdictionCode = Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.allocatedFor = Objects.requireNonNull(allocatedFor, "allocatedFor");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getId()                  { return id; }
    public Tenant getTenant()              { return tenant; }
    public String getJurisdictionCode()    { return jurisdictionCode; }
    public BigDecimal getAmount()          { return amount; }
    public LocalDate getAllocatedFor()     { return allocatedFor; }
    public Instant getCreatedAt()          { return createdAt; }

    // equals/hashCode on the primary key only.
    @Override public boolean equals(Object o) { return o instanceof Allocation other && Objects.equals(id, other.id); }
    @Override public int hashCode()           { return Objects.hashCode(id); }
}
