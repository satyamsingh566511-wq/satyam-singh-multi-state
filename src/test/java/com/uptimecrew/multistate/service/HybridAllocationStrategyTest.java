package com.uptimecrew.multistate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridAllocationStrategyTest {

    private static final String WORKER_ID = "wkr_001";
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("10000.00");
    private static final BigDecimal HALF = new BigDecimal("0.50");

    private static AllocationStrategy dayCount() {
        return new DayCountAllocationStrategy();
    }

    private static AllocationStrategy incomeProportional() {
        return new IncomeProportionalAllocationStrategy(Map.of(
                "CA", new BigDecimal("1.00"),
                "NY", new BigDecimal("3.00")));
    }

    @Test
    void constructor_nullPrimary_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new HybridAllocationStrategy(null, dayCount(), HALF));
    }

    @Test
    void constructor_nullSecondary_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new HybridAllocationStrategy(dayCount(), null, HALF));
    }

    @Test
    void constructor_nullPrimaryWeight_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new HybridAllocationStrategy(dayCount(), incomeProportional(), null));
    }

    @Test
    void constructor_primaryWeightBelowZero_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HybridAllocationStrategy(dayCount(), incomeProportional(), new BigDecimal("-0.01")));
    }

    @Test
    void constructor_primaryWeightAboveOne_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HybridAllocationStrategy(dayCount(), incomeProportional(), new BigDecimal("1.01")));
    }

    @Test
    void constructor_equalPrimaryAndSecondary_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HybridAllocationStrategy(incomeProportional(), incomeProportional(), HALF));
    }

    @Test
    void allocate_blendsPrimaryAndSecondaryByPrimaryWeight() {
        AllocationStrategy strategy = new HybridAllocationStrategy(dayCount(), incomeProportional(), HALF);
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", LocalDate.of(2026, 3, 2)),
                new WorkDay("day_002", WORKER_ID, "CA", LocalDate.of(2026, 3, 3)),
                new WorkDay("day_003", WORKER_ID, "NY", LocalDate.of(2026, 3, 4)),
                new WorkDay("day_004", WORKER_ID, "NY", LocalDate.of(2026, 3, 5)));

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR);

        // DayCount: CA 5000 / NY 5000. IncomeProportional (CA w1, NY w3): CA 2500 / NY 7500.
        // Blend at 0.50: CA = 3750.00, NY = 6250.00.
        assertEquals(2, result.size());
        assertAll(
                () -> assertEquals("CA", result.get(0).jurisdictionCode()),
                () -> assertEquals(new BigDecimal("3750.00"), result.get(0).amount()),
                () -> assertEquals("NY", result.get(1).jurisdictionCode()),
                () -> assertEquals(new BigDecimal("6250.00"), result.get(1).amount()));
    }

    @Test
    void allocate_emptyWorkDays_returnsEmptyList() {
        AllocationStrategy strategy = new HybridAllocationStrategy(dayCount(), incomeProportional(), HALF);

        List<IncomeAllocation> result = strategy.allocate(WORKER_ID, TOTAL_INCOME, List.of(), ALLOCATED_FOR);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void allocate_nullWorkerId_throwsNullPointerException() {
        AllocationStrategy strategy = new HybridAllocationStrategy(dayCount(), incomeProportional(), HALF);

        assertThrows(NullPointerException.class,
                () -> strategy.allocate(null, TOTAL_INCOME, List.of(), ALLOCATED_FOR));
    }

    @Test
    void allocate_blankWorkerId_throwsIllegalArgumentException() {
        AllocationStrategy strategy = new HybridAllocationStrategy(dayCount(), incomeProportional(), HALF);

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate("   ", TOTAL_INCOME, List.of(), ALLOCATED_FOR));
    }

    @Test
    void allocate_negativeTotalIncome_throwsIllegalArgumentException() {
        AllocationStrategy strategy = new HybridAllocationStrategy(dayCount(), incomeProportional(), HALF);

        assertThrows(IllegalArgumentException.class,
                () -> strategy.allocate(WORKER_ID, new BigDecimal("-1.00"), List.of(), ALLOCATED_FOR));
    }

    @Test
    void equals_sameDelegatesAndWeight_areEqualAndShareHashCode() {
        // DayCountAllocationStrategy compares by identity, so share one delegate instance.
        AllocationStrategy primary = dayCount();
        AllocationStrategy secondary = incomeProportional();
        HybridAllocationStrategy a = new HybridAllocationStrategy(primary, secondary, HALF);
        HybridAllocationStrategy b = new HybridAllocationStrategy(primary, secondary, HALF);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentPrimaryWeight_areNotEqual() {
        AllocationStrategy primary = dayCount();
        AllocationStrategy secondary = incomeProportional();
        HybridAllocationStrategy a = new HybridAllocationStrategy(primary, secondary, HALF);
        HybridAllocationStrategy b =
                new HybridAllocationStrategy(primary, secondary, new BigDecimal("0.25"));

        assertNotEquals(a, b);
    }

    @Test
    void toString_containsPrimaryWeight() {
        HybridAllocationStrategy strategy = new HybridAllocationStrategy(dayCount(), incomeProportional(), HALF);

        assertTrue(strategy.toString().contains("primaryWeight"));
    }
}
