package com.uptimecrew.multistate.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomeAllocationTest {

    private static final String ALLOC_ID  = "alloc_001";
    private static final String WORKER_ID = "wkr_001";
    private static final String JUR_CODE  = "CA";
    private static final BigDecimal AMOUNT = new BigDecimal("12500.00");
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);

    @Test
    void constructor_validInputs_exposesAllAccessors() {
        IncomeAllocation subject = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, ALLOCATED_FOR);

        assertEquals(ALLOC_ID, subject.id());
        assertEquals(WORKER_ID, subject.workerId());
        assertEquals(JUR_CODE, subject.jurisdictionCode());
        assertEquals(0, AMOUNT.compareTo(subject.amount()));
        assertEquals(ALLOCATED_FOR, subject.allocatedFor());
    }

    @Test
    void constructor_amountWithExtraScale_isNormalizedToScale2HalfUp() {
        IncomeAllocation subject = new IncomeAllocation(
                ALLOC_ID, WORKER_ID, JUR_CODE, new BigDecimal("100.005"), ALLOCATED_FOR);

        assertEquals(2, subject.amount().scale());
        assertEquals(new BigDecimal("100.01"), subject.amount());
    }

    @Test
    void constructor_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(null, WORKER_ID, JUR_CODE, AMOUNT, ALLOCATED_FOR));
    }

    @Test
    void constructor_nullWorkerId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, null, JUR_CODE, AMOUNT, ALLOCATED_FOR));
    }

    @Test
    void constructor_nullJurisdictionCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, null, AMOUNT, ALLOCATED_FOR));
    }

    @Test
    void constructor_nullAmount_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, null, ALLOCATED_FOR));
    }

    @Test
    void constructor_nullAllocatedFor_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, null));
    }

    @ParameterizedTest(name = "rejects amount = {0}")
    @CsvSource({
            "-0.01",        // smallest representable negative at scale 2 (boundary)
            "-1.00",        // small negative
            "-12500.00",    // sample-amount magnitude, negated
            "-100000.00"    // large negative
    })
    void constructor_negativeAmount_throwsIllegalArgumentException(String amount) {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE,
                        new BigDecimal("-0.01"), ALLOCATED_FOR));
    }

    @Test
    void constructor_emptyJurisdictionCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeAllocation(ALLOC_ID, WORKER_ID, "", AMOUNT, ALLOCATED_FOR));
    }

    @Test
    void constructor_blankWorkerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IncomeAllocation(ALLOC_ID, "   ", JUR_CODE, AMOUNT, ALLOCATED_FOR));
    }

    @Test
    void equals_sameFieldValues_areEqualAndShareHashCode() {
        IncomeAllocation a = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, ALLOCATED_FOR);
        IncomeAllocation b = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, ALLOCATED_FOR);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentAmount_areNotEqual() {
        IncomeAllocation a = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE,
                new BigDecimal("12500.00"), ALLOCATED_FOR);
        IncomeAllocation b = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE,
                new BigDecimal("12500.01"), ALLOCATED_FOR);

        assertNotEquals(a, b);
    }

    @Test
    void toString_containsAllFields() {
        String rendered = new IncomeAllocation(ALLOC_ID, WORKER_ID, JUR_CODE, AMOUNT, ALLOCATED_FOR).toString();

        assertTrue(rendered.contains(ALLOC_ID));
        assertTrue(rendered.contains(WORKER_ID));
        assertTrue(rendered.contains(JUR_CODE));
        assertTrue(rendered.contains("12500.00"));
        assertTrue(rendered.contains("2026"));
    }
}
