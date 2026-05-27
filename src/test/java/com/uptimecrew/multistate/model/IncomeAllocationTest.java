package com.uptimecrew.multistate.model;

import java.math.BigDecimal;
import java.time.Year;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomeAllocationTest {

    private static final String ALLOC_ID  = "alloc_001";
    private static final String WORKER_ID = "wkr_001";
    private static final String JUR_CODE  = "CA";
    private static final BigDecimal AMOUNT = new BigDecimal("12500.00");
    private static final Year YEAR = Year.of(2026);

    @Test
    void constructor_validInputs_exposesAllAccessors() {
        IncomeAllocation subject = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, YEAR);

        assertEquals(ALLOC_ID, subject.id());
        assertEquals(WORKER_ID, subject.workerId());
        assertEquals(JUR_CODE, subject.jurisdictionCode());
        assertEquals(0, AMOUNT.compareTo(subject.amount()));
        assertEquals(YEAR, subject.allocatedFor());
    }

    @Test
    void constructor_amountWithExtraScale_isNormalizedToScale2HalfUp() {
        IncomeAllocation subject = new IncomeAllocation(
                ALLOC_ID, WORKER_ID, JUR_CODE, new BigDecimal("100.005"), YEAR);

        assertEquals(2, subject.amount().scale());
        assertEquals(new BigDecimal("100.01"), subject.amount());
    }

    @Test
    void constructor_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(null, WORKER_ID, JUR_CODE, AMOUNT, YEAR));
    }

    @Test
    void constructor_nullWorkerId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, null, JUR_CODE, AMOUNT, YEAR));
    }

    @Test
    void constructor_nullJurisdictionCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, null, AMOUNT, YEAR));
    }

    @Test
    void constructor_nullAmount_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, null, YEAR));
    }

    @Test
    void constructor_nullAllocatedFor_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, null));
    }

    @Test
    void constructor_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE,
                        new BigDecimal("-0.01"), YEAR));
    }

    @Test
    void constructor_emptyJurisdictionCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, "", AMOUNT, YEAR));
    }

    @Test
    void constructor_blankWorkerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeAllocation(ALLOC_ID, "   ", JUR_CODE, AMOUNT, YEAR));
    }

    @Test
    void equals_sameFieldValues_areEqualAndShareHashCode() {
        IncomeAllocation a = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, YEAR);
        IncomeAllocation b = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, YEAR);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentAmount_areNotEqual() {
        IncomeAllocation a = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE,
                new BigDecimal("12500.00"), YEAR);
        IncomeAllocation b = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE,
                new BigDecimal("12500.01"), YEAR);

        assertNotEquals(a, b);
    }

    @Test
    void toString_containsAllFields() {
        String rendered = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, YEAR).toString();

        assertTrue(rendered.contains(ALLOC_ID));
        assertTrue(rendered.contains(WORKER_ID));
        assertTrue(rendered.contains(JUR_CODE));
        assertTrue(rendered.contains("12500.00"));
        assertTrue(rendered.contains("2026"));
    }
}
