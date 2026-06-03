package com.uptimecrew.multistate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.uptimecrew.multistate.model.IncomeAllocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationRegistryTest {

    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);

    private static IncomeAllocation allocation(String id, String jurisdictionCode, String amount) {
        return new IncomeAllocation(id, "wkr_001", jurisdictionCode, new BigDecimal(amount), ALLOCATED_FOR);
    }

    @Test
    void constructor_nullCollection_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new AllocationRegistry(null));
    }

    @Test
    void constructor_collectionContainingNull_throwsNullPointerException() {
        var withNull = java.util.Arrays.asList(allocation("alloc_001", "CA", "100.00"), null);

        assertThrows(NullPointerException.class, () -> new AllocationRegistry(withNull));
    }

    @Test
    void constructor_duplicateId_throwsIllegalArgumentException() {
        var duplicates = List.of(
                allocation("alloc_001", "CA", "100.00"),
                allocation("alloc_001", "NY", "200.00"));

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new AllocationRegistry(duplicates));
        assertTrue(thrown.getMessage().contains("alloc_001"));
    }

    @Test
    void size_emptyRegistry_isZero() {
        assertEquals(0, new AllocationRegistry(List.of()).size());
    }

    @Test
    void size_multipleAllocations_countsEach() {
        var registry = new AllocationRegistry(List.of(
                allocation("alloc_001", "CA", "100.00"),
                allocation("alloc_002", "NY", "200.00")));

        assertEquals(2, registry.size());
    }

    @Test
    void findById_nullId_throwsNullPointerException() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        assertThrows(NullPointerException.class, () -> registry.findById(null));
    }

    @Test
    void findById_presentId_returnsAllocation() {
        var expected = allocation("alloc_001", "CA", "100.00");
        var registry = new AllocationRegistry(List.of(expected));

        var found = registry.findById("alloc_001");

        assertTrue(found.isPresent());
        assertEquals(expected, found.get());
    }

    @Test
    void findById_absentId_returnsEmpty() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        assertTrue(registry.findById("alloc_999").isEmpty());
    }

    @Test
    void findByJurisdictionAbove_nullJurisdictionCode_throwsNullPointerException() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        assertThrows(NullPointerException.class,
                () -> registry.findByJurisdictionAbove(null, BigDecimal.ZERO));
    }

    @Test
    void findByJurisdictionAbove_nullThreshold_throwsNullPointerException() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        assertThrows(NullPointerException.class,
                () -> registry.findByJurisdictionAbove("CA", null));
    }

    @Test
    void findByJurisdictionAbove_filtersByJurisdictionAndThreshold() {
        var registry = new AllocationRegistry(List.of(
                allocation("alloc_001", "CA", "100.00"),
                allocation("alloc_002", "CA", "50.00"),
                allocation("alloc_003", "NY", "999.00")));

        var result = registry.findByJurisdictionAbove("CA", new BigDecimal("75.00"));

        assertEquals(List.of("alloc_001"), result.stream().map(IncomeAllocation::id).toList());
    }

    @Test
    void findByJurisdictionAbove_thresholdIsExclusive() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        var result = registry.findByJurisdictionAbove("CA", new BigDecimal("100.00"));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByJurisdictionAbove_sortsByAmountDescendingThenId() {
        var registry = new AllocationRegistry(List.of(
                allocation("alloc_b", "CA", "100.00"),
                allocation("alloc_a", "CA", "100.00"),
                allocation("alloc_c", "CA", "300.00")));

        var result = registry.findByJurisdictionAbove("CA", BigDecimal.ZERO);

        assertEquals(List.of("alloc_c", "alloc_a", "alloc_b"),
                result.stream().map(IncomeAllocation::id).toList());
    }

    @Test
    void findByJurisdictionAbove_noMatchingJurisdiction_returnsEmptyList() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        assertTrue(registry.findByJurisdictionAbove("TX", BigDecimal.ZERO).isEmpty());
    }

    @Test
    void findByJurisdictionAbove_returnedListIsUnmodifiable() {
        var registry = new AllocationRegistry(List.of(allocation("alloc_001", "CA", "100.00")));

        var result = registry.findByJurisdictionAbove("CA", BigDecimal.ZERO);

        assertFalse(result.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(allocation("alloc_002", "CA", "200.00")));
    }
}
