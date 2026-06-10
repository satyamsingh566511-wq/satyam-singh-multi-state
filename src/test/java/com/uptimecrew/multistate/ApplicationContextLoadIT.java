package com.uptimecrew.multistate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;
import com.uptimecrew.multistate.service.AllocationService;

/**
 * Integration test (IT suffix — boots a real Spring context, so it is kept out
 * of the fast unit-test set). {@link SpringBootTest} starts the full
 * application context defined by {@link Application} for the lifetime of this
 * class; {@code @ActiveProfiles("test")} makes the {@code test} profile from
 * application.yml win, so no real datasource is required (the W2 D3 Application
 * already excludes {@code DataSourceAutoConfiguration}).
 *
 * <p>Field injection is used deliberately — it is acceptable in tests, where the
 * container, not a caller, owns construction.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadIT {

    @Autowired
    AllocationService service;

    @Test
    void context_loads_and_service_bean_is_wired() {
        // The whole point of this test: prove the context boots and the
        // @Service bean (with its @Primary strategy) is found and injected.
        assertThat(service).isNotNull();
    }

    @Test
    void service_delegates_to_primary_strategy() {
        // The @Primary strategy is DayCountAllocationStrategy: an even split by
        // raw day count. One day in each of two jurisdictions over a $100,000
        // total yields two equal $50,000.00 shares that reconcile to the total.
        var workerId = "wkr_alice";
        var allocatedFor = LocalDate.of(2025, 12, 31);
        var workDays = List.of(
                new WorkDay("day_1", workerId, "US-CA", LocalDate.of(2025, 6, 2)),
                new WorkDay("day_2", workerId, "US-NY", LocalDate.of(2025, 6, 3)));

        List<IncomeAllocation> allocations =
                service.allocate(workerId, new BigDecimal("100000.00"), workDays, allocatedFor);

        assertThat(allocations)
                .hasSize(2)
                .extracting(IncomeAllocation::jurisdictionCode)
                .containsExactly("US-CA", "US-NY");

        assertThat(allocations)
                .extracting(IncomeAllocation::amount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("50000.00"));

        // The audit invariant: the per-jurisdiction shares sum back to the total.
        BigDecimal sum = allocations.stream()
                .map(IncomeAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100000.00");
    }
}
