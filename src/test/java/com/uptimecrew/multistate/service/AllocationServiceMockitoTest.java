package com.uptimecrew.multistate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Proves {@link AllocationService} delegates to its injected
 * {@link AllocationStrategy} without depending on any concrete implementation —
 * the strategy is a Mockito mock, so the only behaviour under test is the
 * service's own delegation and pass-through.
 */
@ExtendWith(MockitoExtension.class)
class AllocationServiceMockitoTest {

    @Mock
    AllocationStrategy strategy;

    private static final String WORKER_ID = "wkr_001";
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("12500.00");
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);

    private static final List<WorkDay> WORK_DAYS = List.of(
            new WorkDay("day_001", WORKER_ID, "CA", LocalDate.of(2026, 3, 1)));

    @Test
    void allocate_validInputs_delegatesToStrategyAndReturnsItsResultUnchanged() {
        // A single allocation summing exactly to the total, so the service's
        // residual reconciliation is a no-op and the strategy's result passes
        List<IncomeAllocation> stubbed = List.of(
                new IncomeAllocation("alloc_001", WORKER_ID, "CA", TOTAL_INCOME, ALLOCATED_FOR));

        when(strategy.allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(stubbed);

        AllocationService subject = new AllocationService(strategy);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR);

        // The strategy was invoked exactly once with the exact inputs the
        // service received (after scale normalisation, which leaves 12500.00)
        verify(strategy).allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR));
        assertEquals(stubbed, result);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("12500.00"), result.get(0).amount());
    }

    @Test
    void allocate_strategyReturnsEmpty_returnsThatSameEmptyResultExactlyOnce() {
        when(strategy.allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(List.of());

        AllocationService subject = new AllocationService(strategy);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR);

        verify(strategy, times(1)).allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR));
        assertTrue(result.isEmpty());
    }
}
