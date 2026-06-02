package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Blends two underlying strategies into a single allocation. Each delegate is
 * run independently over the same inputs and the resulting per-jurisdiction
 * amounts are combined as
 * {@code primaryWeight * primaryAmount + (1 - primaryWeight) * secondaryAmount}.
 * This lets, for example, a day-count split be softened toward an
 * income-proportional one (or vice versa) by a single tunable knob, without
 * either delegate needing to know about the other.
 */
public final class HybridAllocationStrategy implements AllocationStrategy {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final AllocationStrategy primary;
    private final AllocationStrategy secondary;
    private final BigDecimal primaryWeight;

    public HybridAllocationStrategy(AllocationStrategy primary,
                                    AllocationStrategy secondary,
                                    BigDecimal primaryWeight) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.secondary = Objects.requireNonNull(secondary, "secondary");
        Objects.requireNonNull(primaryWeight, "primaryWeight");
        if (primaryWeight.signum() < 0 || primaryWeight.compareTo(ONE) > 0) {
            throw new IllegalArgumentException(
                    "primaryWeight must be within [0, 1]: " + primaryWeight);
        }
        if (primary.equals(secondary)) {
            throw new IllegalArgumentException("primary and secondary must be distinct strategies");
        }
        this.primaryWeight = primaryWeight;
    }

    @Override
    public List<IncomeAllocation> allocate(String workerId,
                                           BigDecimal totalIncome,
                                           List<WorkDay> workDays,
                                           LocalDate allocatedFor) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(totalIncome, "totalIncome");
        Objects.requireNonNull(workDays, "workDays");
        Objects.requireNonNull(allocatedFor, "allocatedFor");
        if (workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (totalIncome.signum() < 0) {
            throw new IllegalArgumentException("totalIncome must not be negative: " + totalIncome);
        }

        if (workDays.isEmpty()) {
            return List.of();
        }

        BigDecimal secondaryWeight = ONE.subtract(primaryWeight);
        Map<String, BigDecimal> blended = new LinkedHashMap<>();
        accumulate(blended, primary.allocate(workerId, totalIncome, workDays, allocatedFor), primaryWeight);
        accumulate(blended, secondary.allocate(workerId, totalIncome, workDays, allocatedFor), secondaryWeight);

        List<IncomeAllocation> allocations = new ArrayList<>(blended.size());
        for (Map.Entry<String, BigDecimal> entry : blended.entrySet()) {
            BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            allocations.add(new IncomeAllocation(
                    "alloc_" + UUID.randomUUID(),
                    workerId,
                    entry.getKey(),
                    amount,
                    allocatedFor));
        }
        return List.copyOf(allocations);
    }

    private static void accumulate(Map<String, BigDecimal> blended,
                                   List<IncomeAllocation> delegateResult,
                                   BigDecimal weight) {
        for (IncomeAllocation allocation : delegateResult) {
            BigDecimal weighted = allocation.amount().multiply(weight);
            blended.merge(allocation.jurisdictionCode(), weighted, BigDecimal::add);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HybridAllocationStrategy other)) return false;
        return primary.equals(other.primary)
                && secondary.equals(other.secondary)
                && primaryWeight.compareTo(other.primaryWeight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(primary, secondary, primaryWeight.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "HybridAllocationStrategy{primary=" + primary
                + ", secondary=" + secondary
                + ", primaryWeight=" + primaryWeight.toPlainString() + "}";
    }
}
