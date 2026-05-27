package com.uptimecrew.multistate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayCountAllocationStrategyTest {

    private static final String WORKER_ID = "wkr_001";
    private static final Year YEAR = Year.of(2026);
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("12500.00");

    @Test
    void allocate_threeCaliforniaDaysOneNewYorkDay_splitsIncomeProportionallyByDayCount() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", LocalDate.of(2026, 3, 1)),
                new WorkDay("day_002", WORKER_ID, "CA", LocalDate.of(2026, 3, 2)),
                new WorkDay("day_003", WORKER_ID, "CA", LocalDate.of(2026, 3, 3)),
                new WorkDay("day_004", WORKER_ID, "NY", LocalDate.of(2026, 3, 4))
        );

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, YEAR);

        assertNotNull(result);
        assertEquals(2, result.size());

        IncomeAllocation california = result.get(0);
        IncomeAllocation newYork    = result.get(1);

        assertAll(
                () -> assertEquals(WORKER_ID, california.workerId()),
                () -> assertEquals("CA", california.jurisdictionCode()),
                () -> assertEquals(YEAR, california.allocatedFor()),
                () -> assertEquals(new BigDecimal("9375.00"), california.amount()),
                () -> assertTrue(california.id().startsWith("alloc_")),

                () -> assertEquals(WORKER_ID, newYork.workerId()),
                () -> assertEquals("NY", newYork.jurisdictionCode()),
                () -> assertEquals(YEAR, newYork.allocatedFor()),
                () -> assertEquals(new BigDecimal("3125.00"), newYork.amount())
        );
    }

    @Test
    void allocate_emptyWorkDays_returnsEmptyList() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, List.of(), YEAR);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void allocate_allDaysInSameJurisdiction_assignsFullIncomeToThatJurisdiction() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", LocalDate.of(2026, 3, 1)),
                new WorkDay("day_002", WORKER_ID, "CA", LocalDate.of(2026, 3, 2))
        );

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, YEAR);

        assertEquals(1, result.size());
        assertEquals("CA", result.get(0).jurisdictionCode());
        assertEquals(new BigDecimal("12500.00"), result.get(0).amount());
    }

    @Test
    void allocate_nullWorkerId_throwsNullPointerException() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        assertThrows(NullPointerException.class,
                () -> strategy.allocate(null, TOTAL_INCOME, List.of(), YEAR));
    }

    @Test
    void allocate_nullTotalIncome_throwsNullPointerException() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        assertThrows(NullPointerException.class,
                () -> strategy.allocate(WORKER_ID, null, List.of(), YEAR));
    }

    @Test
    void allocate_negativeTotalIncome_throwsIllegalArgumentException() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate(WORKER_ID, new BigDecimal("-1.00"), List.of(), YEAR));
    }

    @Test
    void allocate_workDayBelongsToDifferentWorker_throwsIllegalArgumentException() {
        AllocationStrategy strategy = new DayCountAllocationStrategy();

        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", "wkr_999", "CA", LocalDate.of(2026, 3, 1))
        );

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, YEAR));
    }
}
