package com.uptimecrew.multistate.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fluent test-data builder for {@link IncomeAllocation}. Defaults are valid —
 * they satisfy the record's compact-constructor validation (no nulls, non-blank
 * identifiers, non-negative amount) — so a bare
 * {@code aIncomeAllocation().build()} always succeeds, and each test overrides
 * only the field it cares about.
 *
 * <p>This is a production utility (it lives in the {@code model} package, not a
 * test source set) so it is compile-visible to every source set that depends on
 * the main classes.
 */
public final class IncomeAllocationTestDataBuilder {

    private String id = "alloc_001";
    private String workerId = "wkr_001";
    private String jurisdictionCode = "CA";
    private BigDecimal amount = new BigDecimal("125000.00");
    private LocalDate allocatedFor = LocalDate.of(2026, 1, 1);

    public IncomeAllocationTestDataBuilder withId(String value) {
        this.id = value;
        return this;
    }

    public IncomeAllocationTestDataBuilder withWorkerId(String value) {
        this.workerId = value;
        return this;
    }

    public IncomeAllocationTestDataBuilder withJurisdictionCode(String value) {
        this.jurisdictionCode = value;
        return this;
    }

    public IncomeAllocationTestDataBuilder withAmount(BigDecimal value) {
        this.amount = value;
        return this;
    }

    public IncomeAllocationTestDataBuilder withAllocatedFor(LocalDate value) {
        this.allocatedFor = value;
        return this;
    }

    public IncomeAllocation build() {
        return new IncomeAllocation(id, workerId, jurisdictionCode, amount, allocatedFor);
    }

    /** Factory: call as {@code IncomeAllocationTestDataBuilder.aIncomeAllocation().withX(...).build()}. */
    public static IncomeAllocationTestDataBuilder aIncomeAllocation() {
        return new IncomeAllocationTestDataBuilder();
    }
}
