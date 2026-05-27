package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DayCountAllocationStrategy implements AllocationStrategy {

    @Override
    public List<IncomeAllocation> allocate(String workerId,
                                           BigDecimal totalIncome,
                                           List<WorkDay> workDays,
                                           Year allocatedFor) {
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

        Map<String, Long> daysByJurisdiction = new LinkedHashMap<>();
        for (WorkDay day : workDays) {
            if (!workerId.equals(day.workerId())) {
                throw new IllegalArgumentException(
                        "workDay " + day.id() + " belongs to worker " + day.workerId()
                                + ", expected " + workerId);
            }
            daysByJurisdiction.merge(day.jurisdictionCode(), 1L, Long::sum);
        }

        BigDecimal totalDays = BigDecimal.valueOf(workDays.size());
        List<IncomeAllocation> allocations = new ArrayList<>(daysByJurisdiction.size());
        for (Map.Entry<String, Long> entry : daysByJurisdiction.entrySet()) {
            BigDecimal share = BigDecimal.valueOf(entry.getValue())
                    .divide(totalDays, MathContext.DECIMAL64);
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
}
