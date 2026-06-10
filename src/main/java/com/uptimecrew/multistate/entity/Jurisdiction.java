package com.uptimecrew.multistate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

// Reference entity — taxing authorities (countries, states). The stable natural
// code (e.g. 'US-CA') IS the identity, so the PK column is `code`, not `id`.
// A pure reference table: no @OneToMany collection, just mapped columns.
@Entity
@Table(schema = "multistate", name = "jurisdiction")
public class Jurisdiction {

    @Id
    @Column(name = "code", length = 64)
    private String code;                            // TEXT natural-key id, application-supplied, never @GeneratedValue

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "kind", nullable = false)
    private String kind;                            // closed taxonomy: COUNTRY/STATE/PROVINCE/CITY

    @Column(name = "has_income_tax", nullable = false)
    private boolean hasIncomeTax;

    // marginal tax rate, a fraction in [0,1] — NUMERIC(5,4), BigDecimal not double.
    // Nullable: unknown or not-applicable for no-income-tax jurisdictions.
    @Column(name = "top_marginal_rate", nullable = true, precision = 5, scale = 4)
    private BigDecimal topMarginalRate;

    protected Jurisdiction() {}                     // required by JPA

    public Jurisdiction(String code,
                        String name,
                        String kind,
                        boolean hasIncomeTax,
                        BigDecimal topMarginalRate) {
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.hasIncomeTax = hasIncomeTax;
        this.topMarginalRate = topMarginalRate;     // nullable
    }

    public String getCode()              { return code; }
    public String getName()              { return name; }
    public String getKind()              { return kind; }
    public boolean isHasIncomeTax()      { return hasIncomeTax; }
    public BigDecimal getTopMarginalRate() { return topMarginalRate; }

    // equals/hashCode on the primary key only.
    @Override public boolean equals(Object o) { return o instanceof Jurisdiction other && Objects.equals(code, other.code); }
    @Override public int hashCode()           { return Objects.hashCode(code); }
}
