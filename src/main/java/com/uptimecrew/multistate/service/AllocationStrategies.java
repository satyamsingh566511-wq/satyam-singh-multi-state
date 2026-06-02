package com.uptimecrew.multistate.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Factory for {@link AllocationStrategy} instances. The factory methods are
 * named for the behavioural variant they produce — how income is split —
 * rather than the implementing class, and they return the {@code
 * AllocationStrategy} interface so callers never couple to a concrete type.
 */
public final class AllocationStrategies {

    private AllocationStrategies() {
        throw new AssertionError("AllocationStrategies is a static factory and must not be instantiated");
    }

    /** Splits income evenly across jurisdictions by the number of days worked in each. */
    public static AllocationStrategy byDayCount() {
        return new DayCountAllocationStrategy();
    }

    /** Splits income by {@code daysWorked * weight}, weighting each jurisdiction's earning power. */
    public static AllocationStrategy weightedByIncome(Map<String, BigDecimal> jurisdictionWeights) {
        return new IncomeProportionalAllocationStrategy(jurisdictionWeights);
    }

    /**
     * Blends two strategies, weighting {@code primary} by {@code primaryWeight}
     * and {@code secondary} by its complement (both in {@code [0, 1]}).
     */
    public static AllocationStrategy blendOf(AllocationStrategy primary,
                                             AllocationStrategy secondary,
                                             BigDecimal primaryWeight) {
        return new HybridAllocationStrategy(primary, secondary, primaryWeight);
    }
}
