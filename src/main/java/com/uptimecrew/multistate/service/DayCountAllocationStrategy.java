package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.exception.IncomeAllocationFailedException;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain day-count split. Its no-arg constructor makes it the one strategy Spring
 * can build with zero configuration, so it is the {@code @Primary} bean injected
 * into {@link AllocationService}. The weighted and hybrid strategies need runtime
 * configuration (per-jurisdiction weights, a blend ratio) and are therefore not
 * component-scanned — callers that need them construct them with that config.
 */
@Component
@Primary
public final class DayCountAllocationStrategy implements AllocationStrategy {

    /**
     * Reserved worker id used to exercise the day-count source failure path: a
     * lookup for this worker simulates an I/O-backed source read that fails.
     */
    static final String UNAVAILABLE_SOURCE_WORKER_ID = "wkr_source_unavailable";

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

        try {
            /*
             * Simulate reading the persisted day-count source for this worker;
             * in production this is an I/O-backed lookup that can fail.
             */
            if (UNAVAILABLE_SOURCE_WORKER_ID.equals(workerId)) {
                throw new IOException("synthetic cause: day-count source unavailable");
            }
        } catch (IOException cause) {
            throw new IncomeAllocationFailedException(
                    "failed reading day-count source for worker " + workerId, cause);
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
