package com.uptimecrew.multistate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.uptimecrew.multistate.exception.IncomeAllocationFailedException;
import com.uptimecrew.multistate.exception.JurisdictionUnsupportedException;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeightedDayCountAllocationStrategyTest {

    private static final String WORKER_ID = "wkr_001";
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("10000.00");

    // March 2026 calendar anchors used throughout these tests.
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 3);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 3, 4);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 3, 7);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 3, 8);

    private static final Map<String, BigDecimal> WEIGHTS = Map.of(
            "CA", new BigDecimal("1.00"),
            "NY", new BigDecimal("2.00"));

    @Test
    @DisplayName("allocate, business days across two jurisdictions, weights shares by the per-state factor")
    void allocate_businessDaysAcrossTwoJurisdictions_weightsSharesByStateFactor() {
        // Arrange: two CA business days (weight 1.00) and one NY business day (weight 2.00).
        WeightedDayCountAllocationStrategy subject = new WeightedDayCountAllocationStrategy(WEIGHTS);
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", MONDAY),
                new WorkDay("day_002", WORKER_ID, "CA", TUESDAY),
                new WorkDay("day_003", WORKER_ID, "NY", WEDNESDAY));

        // Act: split the income across jurisdictions by weighted business-day count.
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR);

        // Assert: CA weighted = 2*1.00 = 2.00, NY weighted = 1*2.00 = 2.00, so each gets half.
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(IncomeAllocation::jurisdictionCode, IncomeAllocation::amount)
                .containsExactlyInAnyOrder(
                        tuple("CA", new BigDecimal("5000.00")),
                        tuple("NY", new BigDecimal("5000.00")));
    }

    @Test
    @DisplayName("allocate, business day in an unconfigured jurisdiction, throws jurisdiction unsupported")
    void allocate_unsupportedJurisdiction_throwsJurisdictionUnsupportedException() {
        // Arrange: a business work day in TX, which has no configured weight.
        WeightedDayCountAllocationStrategy subject = new WeightedDayCountAllocationStrategy(WEIGHTS);
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "TX", MONDAY));

        // Act + Assert: the unsupported jurisdiction is rejected with the Day 4 domain exception.
        assertThatThrownBy(() -> subject.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR))
                .isInstanceOf(JurisdictionUnsupportedException.class)
                .hasMessageContaining("TX");
    }

    @Test
    @DisplayName("allocate, weekend days mixed in, excludes them and allocates only business days")
    void allocate_weekendDaysPresent_excludesThemFromAllocation() {
        // Arrange: CA worked one weekday; NY worked only the weekend, so NY has no business days.
        WeightedDayCountAllocationStrategy subject = new WeightedDayCountAllocationStrategy(WEIGHTS);
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", MONDAY),
                new WorkDay("day_002", WORKER_ID, "NY", SATURDAY),
                new WorkDay("day_003", WORKER_ID, "NY", SUNDAY));

        // Act: only the single CA business day should drive the allocation.
        List<IncomeAllocation> result = subject.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR);

        // Assert: NY is dropped entirely; CA absorbs the full income.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).jurisdictionCode()).isEqualTo("CA");
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("allocate, no business days recorded, throws income allocation failed")
    void allocate_noBusinessDays_throwsIncomeAllocationFailedException() {
        // Arrange: every recorded day falls on a weekend, leaving nothing to weight.
        WeightedDayCountAllocationStrategy subject = new WeightedDayCountAllocationStrategy(WEIGHTS);
        List<WorkDay> workDays = List.of(
                new WorkDay("day_001", WORKER_ID, "CA", SATURDAY),
                new WorkDay("day_002", WORKER_ID, "NY", SUNDAY));

        // Act + Assert: an allocation with zero weighted business days fails with the Day 4 exception.
        assertThatThrownBy(() -> subject.allocate(WORKER_ID, TOTAL_INCOME, workDays, ALLOCATED_FOR))
                .isInstanceOf(IncomeAllocationFailedException.class)
                .hasMessageContaining("business day");
    }
}
