package com.uptimecrew.multistate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;
import com.uptimecrew.multistate.outbox.EventOutboxRepository;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import com.uptimecrew.multistate.repository.AllocationRepository;
import com.uptimecrew.multistate.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
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

    @Mock
    TenantRepository repository;

    @Mock
    TenantReadModelRepository readModelRepository;

    @Mock
    EventOutboxRepository outboxRepository;

    @Mock
    AllocationRepository allocationRepository;

    @Mock
    ObjectMapper objectMapper;

    @BeforeEach
    void stubRepositorySave() throws Exception {
        /* Canonical save stub: return the entity passed in, unchanged. */
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
    }

    private static final String WORKER_ID = "wkr_001";
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("12500.00");
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);

    private static final List<WorkDay> WORK_DAYS = List.of(
            new WorkDay("day_001", WORKER_ID, "CA", LocalDate.of(2026, 3, 1)));

    @Test
    void allocate_validInputs_delegatesToStrategyAndReturnsItsResultUnchanged() {
        /*
         * A single allocation summing exactly to the total, so the service's
         * residual reconciliation is a no-op and the strategy's result passes
         */
        List<IncomeAllocation> stubbed = List.of(
                new IncomeAllocation("alloc_001", WORKER_ID, "CA", TOTAL_INCOME, ALLOCATED_FOR));

        when(strategy.allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(stubbed);

        AllocationService subject = new AllocationService(strategy, repository, readModelRepository, outboxRepository, allocationRepository, objectMapper);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR);

        /*
         * The strategy was invoked exactly once with the exact inputs the
         * service received (after scale normalisation, which leaves 12500.00)
         */
        verify(strategy).allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR));
        assertEquals(stubbed, result);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("12500.00"), result.get(0).amount());
    }

    @Test
    void allocate_strategyReturnsEmpty_returnsThatSameEmptyResultExactlyOnce() {
        when(strategy.allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(List.of());

        AllocationService subject = new AllocationService(strategy, repository, readModelRepository, outboxRepository, allocationRepository, objectMapper);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR);

        verify(strategy, times(1)).allocate(eq(WORKER_ID), eq(TOTAL_INCOME), eq(WORK_DAYS), eq(ALLOCATED_FOR));
        assertTrue(result.isEmpty());
    }

    /**
     * Residual reconciliation when the strategy output sums to a cent LESS than
     * the total — the classic proportional-rounding shortfall. The leftover cent
     * must land on the largest allocation, and the result must sum exactly to the
     * total. Stubbing the strategy lets us force a precise residual that a real
     * strategy would only produce by coincidence.
     */
    @Test
    void allocate_strategyOutputUndershootsTotalByOneCent_assignsResidualToLargestAllocation() {
        BigDecimal total = new BigDecimal("100.00");
        /* 50.00 + 30.00 + 19.99 = 99.99, one cent short of the total. */
        List<IncomeAllocation> understated = List.of(
                new IncomeAllocation("alloc_ca", WORKER_ID, "CA", new BigDecimal("50.00"), ALLOCATED_FOR),
                new IncomeAllocation("alloc_ny", WORKER_ID, "NY", new BigDecimal("30.00"), ALLOCATED_FOR),
                new IncomeAllocation("alloc_tx", WORKER_ID, "TX", new BigDecimal("19.99"), ALLOCATED_FOR));

        when(strategy.allocate(eq(WORKER_ID), eq(total), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(understated);

        AllocationService subject = new AllocationService(strategy, repository, readModelRepository, outboxRepository, allocationRepository, objectMapper);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, total, WORK_DAYS, ALLOCATED_FOR);

        /* Original list order is preserved; only the largest line absorbs the cent. */
        assertEquals("CA", result.get(0).jurisdictionCode());
        assertEquals(new BigDecimal("50.01"), result.get(0).amount());
        assertEquals(new BigDecimal("30.00"), result.get(1).amount());
        assertEquals(new BigDecimal("19.99"), result.get(2).amount());
        assertEquals(total, sumOf(result));
    }

    /**
     * The mirror case: the strategy output sums to a cent MORE than the total
     * (over-allocation). The surplus cent must be reclaimed from the largest
     * allocation so the audit total still reconciles exactly.
     */
    @Test
    void allocate_strategyOutputOvershootsTotalByOneCent_reclaimsResidualFromLargestAllocation() {
        BigDecimal total = new BigDecimal("100.00");
        /* 50.00 + 30.00 + 20.01 = 100.01, one cent over the total. */
        List<IncomeAllocation> overstated = List.of(
                new IncomeAllocation("alloc_ca", WORKER_ID, "CA", new BigDecimal("50.00"), ALLOCATED_FOR),
                new IncomeAllocation("alloc_ny", WORKER_ID, "NY", new BigDecimal("30.00"), ALLOCATED_FOR),
                new IncomeAllocation("alloc_tx", WORKER_ID, "TX", new BigDecimal("20.01"), ALLOCATED_FOR));

        when(strategy.allocate(eq(WORKER_ID), eq(total), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(overstated);

        AllocationService subject = new AllocationService(strategy, repository, readModelRepository, outboxRepository, allocationRepository, objectMapper);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, total, WORK_DAYS, ALLOCATED_FOR);

        assertEquals(new BigDecimal("49.99"), result.get(0).amount());
        assertEquals(new BigDecimal("30.00"), result.get(1).amount());
        assertEquals(new BigDecimal("20.01"), result.get(2).amount());
        assertEquals(total, sumOf(result));
    }

    /**
     * A multi-cent residual must be spread one cent at a time across the
     * allocations round-robin (largest first), never dumped on a single line.
     * Three equal lines short by a full dollar means 100 cents distributed as
     * 34/33/33, with the extra cent going to the first line by the tie-break.
     */
    @Test
    void allocate_strategyOutputShortByManyCents_spreadsResidualAcrossAllocations() {
        BigDecimal total = new BigDecimal("100.00");
        /* 33.00 * 3 = 99.00, a full dollar (100 cents) short of the total. */
        List<IncomeAllocation> shortfall = List.of(
                new IncomeAllocation("alloc_ca", WORKER_ID, "CA", new BigDecimal("33.00"), ALLOCATED_FOR),
                new IncomeAllocation("alloc_ny", WORKER_ID, "NY", new BigDecimal("33.00"), ALLOCATED_FOR),
                new IncomeAllocation("alloc_tx", WORKER_ID, "TX", new BigDecimal("33.00"), ALLOCATED_FOR));

        when(strategy.allocate(eq(WORKER_ID), eq(total), eq(WORK_DAYS), eq(ALLOCATED_FOR)))
                .thenReturn(shortfall);

        AllocationService subject = new AllocationService(strategy, repository, readModelRepository, outboxRepository, allocationRepository, objectMapper);
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, total, WORK_DAYS, ALLOCATED_FOR);

        assertEquals(new BigDecimal("33.34"), result.get(0).amount());
        assertEquals(new BigDecimal("33.33"), result.get(1).amount());
        assertEquals(new BigDecimal("33.33"), result.get(2).amount());
        assertEquals(total, sumOf(result));
    }

    private static BigDecimal sumOf(List<IncomeAllocation> allocations) {
        return allocations.stream()
                .map(IncomeAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
