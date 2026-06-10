package com.uptimecrew.multistate.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Primary entity — one row per taxpayer entity (the Week 1 "worker").
// JPA needs a no-arg constructor (protected is fine) AND mutable state, so
// entities cannot be Java records. State is private; expose getters only.
@Entity
@Table(schema = "multistate", name = "tenant")
public class Tenant {

    @Id
    @Column(length = 64)
    private String id;                                              // TEXT id, application-generated, never @GeneratedValue

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "external_ref", nullable = false)
    private String externalRef;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "residency_jurisdiction_code", nullable = true)  // nullable: residency may be unknown at onboarding
    private String residencyJurisdictionCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "tenant",
               fetch = FetchType.LAZY,                              // LAZY to dodge N+1
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    private List<Allocation> allocations = new ArrayList<>();

    protected Tenant() {}                                           // required by JPA

    public Tenant(String id,
                  String displayName,
                  String externalRef,
                  String status,
                  String residencyJurisdictionCode,
                  Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.externalRef = Objects.requireNonNull(externalRef, "externalRef");
        this.status = Objects.requireNonNull(status, "status");
        this.residencyJurisdictionCode = residencyJurisdictionCode;   // nullable
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getId()                          { return id; }
    public String getDisplayName()                 { return displayName; }
    public String getExternalRef()                 { return externalRef; }
    public String getStatus()                      { return status; }
    public String getResidencyJurisdictionCode()   { return residencyJurisdictionCode; }
    public Instant getCreatedAt()                  { return createdAt; }
    public List<Allocation> getAllocations()       { return allocations; }

    // equals/hashCode on the primary key only — never on the lazy collection.
    @Override public boolean equals(Object o) { return o instanceof Tenant other && Objects.equals(id, other.id); }
    @Override public int hashCode()           { return Objects.hashCode(id); }
}
