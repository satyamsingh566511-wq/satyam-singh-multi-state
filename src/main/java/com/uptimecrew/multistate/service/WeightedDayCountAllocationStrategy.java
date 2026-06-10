package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.exception.IncomeAllocationFailedException;
import com.uptimecrew.multistate.exception.JurisdictionUnsupportedException;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Allocates income by day count, but counts only business days (Monday through
 * Friday) and scales each jurisdiction's business-day count by a configured
 * per-state weight. A jurisdiction's share is its
 * {@code (businessDayCount * weight)} over the sum of all such products. Days
 * worked on a weekend are ignored entirely, and a jurisdiction with no business
 * days drops out of the result.
 */
@Component
public final class WeightedDayCountAllocationStrategy implements AllocationStrategy {

    private final Map<String, BigDecimal> jurisdictionWeights;

    /**
     * Spring-default constructor. This strategy is genuinely parameterized by a
     * per-jurisdiction weight map, which the container cannot synthesise, so this
     * no-arg constructor seeds a minimal valid placeholder ({@code US-CA} at
     * weight 1) purely so the bean can be component-scanned. The {@code @Primary}
     * {@link DayCountAllocationStrategy} is what {@link AllocationService} injects;
     * a real weighted allocation constructs an instance with explicit,
     * caller-supplied weights via {@link #WeightedDayCountAllocationStrategy(Map)}.
     */
    public WeightedDayCountAllocationStrategy() {
        this(Map.of("US-CA", BigDecimal.ONE));
    }

    public WeightedDayCountAllocationStrategy(Map<String, BigDecimal> jurisdictionWeights) {
        Objects.requireNonNull(jurisdictionWeights, "jurisdictionWeights");
        if (jurisdictionWeights.isEmpty()) {
            throw new IllegalArgumentException("jurisdictionWeights must not be empty");
        }
        Map<String, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : jurisdictionWeights.entrySet()) {
            String code = Objects.requireNonNull(entry.getKey(), "jurisdiction code");
            BigDecimal weight = Objects.requireNonNull(entry.getValue(),
                    "weight for jurisdiction " + entry.getKey());
            if (code.isBlank()) {
                throw new IllegalArgumentException("jurisdiction code must not be blank");
            }
            if (weight.signum() <= 0) {
                throw new IllegalArgumentException(
                        "weight for jurisdiction " + code + " must be positive: " + weight);
            }
            copy.put(code, weight);
        }
        this.jurisdictionWeights = Map.copyOf(copy);
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

        Map<String, Long> businessDaysByJurisdiction = countBusinessDays(workerId, workDays);

        Map<String, BigDecimal> weightedByJurisdiction = new LinkedHashMap<>();
        BigDecimal totalWeighted = BigDecimal.ZERO;
        for (Map.Entry<String, Long> entry : businessDaysByJurisdiction.entrySet()) {
            BigDecimal weighted = jurisdictionWeights.get(entry.getKey())
                    .multiply(BigDecimal.valueOf(entry.getValue()));
            weightedByJurisdiction.put(entry.getKey(), weighted);
            totalWeighted = totalWeighted.add(weighted);
        }

        if (totalWeighted.signum() == 0) {
            throw new IncomeAllocationFailedException(
                    "no business day recorded for worker " + workerId + " to allocate income against");
        }

        List<IncomeAllocation> allocations = new ArrayList<>(weightedByJurisdiction.size());
        for (Map.Entry<String, BigDecimal> entry : weightedByJurisdiction.entrySet()) {
            BigDecimal share = entry.getValue().divide(totalWeighted, MathContext.DECIMAL64);
            BigDecimal amount = totalIncome.multiply(share)
                    .setScale(2, RoundingMode.HALF_UP);
            allocations.add(new IncomeAllocation(
                    "alloc_" + UUID.randomUUID(),
                    workerId,
                    entry.getKey(),
                    amount,
                    allocatedFor));
        }
        return List.copyOf(allocations);
    }

    /**
     * Tallies business days (Mon-Fri) per jurisdiction, validating that each work
     * day belongs to {@code workerId} and names a configured jurisdiction.
     * Weekend days are validated but not counted.
     */
    private Map<String, Long> countBusinessDays(String workerId, List<WorkDay> workDays) {
        Map<String, Long> businessDaysByJurisdiction = new LinkedHashMap<>();
        for (WorkDay day : workDays) {
            if (!workerId.equals(day.workerId())) {
                throw new IllegalArgumentException(
                        "workDay " + day.id() + " belongs to worker " + day.workerId()
                                + ", expected " + workerId);
            }
            String code = day.jurisdictionCode();
            if (!jurisdictionWeights.containsKey(code)) {
                throw new JurisdictionUnsupportedException(
                        "jurisdiction " + code + " is not supported by this strategy: "
                                + "no weight configured (work day " + day.id() + ")");
            }
            if (isBusinessDay(day.workedOn())) {
                businessDaysByJurisdiction.merge(code, 1L, Long::sum);
            }
        }
        return businessDaysByJurisdiction;
    }

    private static boolean isBusinessDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeightedDayCountAllocationStrategy other)) return false;
        return jurisdictionWeights.equals(other.jurisdictionWeights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jurisdictionWeights);
    }

    @Override
    public String toString() {
        return "WeightedDayCountAllocationStrategy{jurisdictionWeights=" + jurisdictionWeights + "}";
    }
}
