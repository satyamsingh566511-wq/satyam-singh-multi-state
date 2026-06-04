package com.uptimecrew.multistate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uptimecrew.multistate.exception.JurisdictionUnsupportedException;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomeProportionalAllocationStrategyTest {

    private static final String WORKER_ID = "wkr_001";
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("10000.00");
    private static final Map<String, BigDecimal> WEIGHTS = Map.of(
            "CA", new BigDecimal("1.00"),
            "NY", new BigDecimal("3.00"));

    private static IncomeProportionalAllocationStrategy strategyWithWeights() {
        return new IncomeProportionalAllocationStrategy(WEIGHTS);
    }

    @Test
    void constructor_nullWeights_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeProportionalAllocationStrategy(null));
    }

    @Test
    void constructor_emptyWeights_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeProportionalAllocationStrategy(Map.of()));
    }

    @Test
    void constructor_nullWeightValue_throwsNullPointerException() {
        Map<String, BigDecimal> weights = new HashMap<>();
        weights.put("CA", null);

        assertThrows(NullPointerException.class,
                () -> new IncomeProportionalAllocationStrategy(weights));
    }

    @Test
    void constructor_blankJurisdictionCode_throwsIllegalArgumentException() {
        Map<String, BigDecimal> weights = Map.of("  ", new BigDecimal("1.00"));

        assertThrows(IllegalArgumentException.class,
                () -> new IncomeProportionalAllocationStrategy(weights));
    }

    @Test
    void constructor_nonPositiveWeight_throwsIllegalArgumentException() {
        Map<String, BigDecimal> weights = Map.of("CA", new BigDecimal("0.00"));

        assertThrows(IllegalArgumentException.class,
                () -> new IncomeProportionalAllocationStrategy(weights));
    }

    @Test
    void allocate_weightedDaysAcrossTwoJurisdictions_splitsByWeightedShare() {
        AllocationStrategy strategy = strategyWithWeights();
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", LocalDate.of(2026, 3, 2)),
                new WorkDay("day_002", WORKER_ID, "CA", LocalDate.of(2026, 3, 3)),
                new WorkDay("day_003", WORKER_ID, "NY", LocalDate.of(2026, 3, 4)));

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR);

        // CA weighted = 2*1 = 2, NY weighted = 1*3 = 3, total = 5 -> CA 40%, NY 60%.
        assertEquals(2, result.size());
        assertAll(
                () -> assertEquals("CA", result.get(0).jurisdictionCode()),
                () -> assertEquals(new BigDecimal("4000.00"), result.get(0).amount()),
                () -> assertEquals("NY", result.get(1).jurisdictionCode()),
                () -> assertEquals(new BigDecimal("6000.00"), result.get(1).amount()));
    }

    @Test
    void allocate_emptyWorkDays_returnsEmptyList() {
        AllocationStrategy strategy = strategyWithWeights();

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, List.of(), ALLOCATED_FOR);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void allocate_unsupportedJurisdiction_throwsJurisdictionUnsupportedException() {
        AllocationStrategy strategy = strategyWithWeights();
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "TX", LocalDate.of(2026, 3, 2)));

        assertThrows(JurisdictionUnsupportedException.class,
                () -> strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR));
    }

    @Test
    void allocate_workDayBelongsToDifferentWorker_throwsIllegalArgumentException() {
        AllocationStrategy strategy = strategyWithWeights();
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", "wkr_999", "CA", LocalDate.of(2026, 3, 2)));

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR));
    }

    @Test
    void allocate_nullWorkerId_throwsNullPointerException() {
        AllocationStrategy strategy = strategyWithWeights();

        assertThrows(NullPointerException.class,
                () -> strategy.allocate(null, TOTAL_INCOME, List.of(), ALLOCATED_FOR));
    }

    @Test
    void allocate_blankWorkerId_throwsIllegalArgumentException() {
        AllocationStrategy strategy = strategyWithWeights();

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate("   ", TOTAL_INCOME, List.of(), ALLOCATED_FOR));
    }

    @Test
    void allocate_negativeTotalIncome_throwsIllegalArgumentException() {
        AllocationStrategy strategy = strategyWithWeights();

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate(WORKER_ID, new BigDecimal("-1.00"), List.of(), ALLOCATED_FOR));
    }

    @Test
    void equals_sameWeights_areEqualAndShareHashCode() {
        IncomeProportionalAllocationStrategy a = strategyWithWeights();
        IncomeProportionalAllocationStrategy b = strategyWithWeights();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentWeights_areNotEqual() {
        IncomeProportionalAllocationStrategy a = strategyWithWeights();
        IncomeProportionalAllocationStrategy b =
                new IncomeProportionalAllocationStrategy(Map.of("CA", new BigDecimal("2.00")));

        assertNotEquals(a, b);
    }

    @Test
    void toString_containsWeights() {
        assertTrue(strategyWithWeights().toString().contains("CA"));
    }
}
