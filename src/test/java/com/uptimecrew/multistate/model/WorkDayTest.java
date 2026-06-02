package com.uptimecrew.multistate.model;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkDayTest {

    private static final String DAY_ID    = "day_001";
    private static final String WORKER_ID = "wkr_001";
    private static final String JUR_CODE  = "CA";
    private static final LocalDate WORKED_ON = LocalDate.of(2026, 3, 1);

    @Test
    void constructor_validInputs_exposesAllAccessors() {
        WorkDay subject = new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, WORKED_ON);

        assertEquals(DAY_ID, subject.id());
        assertEquals(WORKER_ID, subject.workerId());
        assertEquals(JUR_CODE, subject.jurisdictionCode());
        assertEquals(WORKED_ON, subject.workedOn());
    }

    @Test
    void constructor_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new WorkDay(null, WORKER_ID, JUR_CODE, WORKED_ON));
    }

    @Test
    void constructor_nullWorkerId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new WorkDay(DAY_ID, null, JUR_CODE, WORKED_ON));
    }

    @Test
    void constructor_nullJurisdictionCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new WorkDay(DAY_ID, WORKER_ID, null, WORKED_ON));
    }

    @Test
    void constructor_nullWorkedOn_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, null));
    }

    @Test
    void constructor_emptyId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkDay("", WORKER_ID, JUR_CODE, WORKED_ON));
    }

    @Test
    void constructor_blankWorkerId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkDay(DAY_ID, "   ", JUR_CODE, WORKED_ON));
    }

    @Test
    void constructor_emptyJurisdictionCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkDay(DAY_ID, WORKER_ID, "", WORKED_ON));
    }

    @Test
    void equals_sameFieldValues_areEqualAndShareHashCode() {
        WorkDay a = new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, WORKED_ON);
        WorkDay b = new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, WORKED_ON);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentWorkedOn_areNotEqual() {
        WorkDay march = new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, LocalDate.of(2026, 3, 1));
        WorkDay april = new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, LocalDate.of(2026, 4, 1));

        assertNotEquals(march, april);
    }

    @Test
    void toString_containsIdAndWorkerIdAndJurisdictionAndDate() {
        String rendered = new WorkDay(DAY_ID, WORKER_ID, JUR_CODE, WORKED_ON).toString();

        assertTrue(rendered.contains(DAY_ID));
        assertTrue(rendered.contains(WORKER_ID));
        assertTrue(rendered.contains(JUR_CODE));
        assertTrue(rendered.contains("2026-03-01"));
    }
}
