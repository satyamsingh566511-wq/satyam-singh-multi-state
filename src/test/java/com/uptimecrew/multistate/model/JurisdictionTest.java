package com.uptimecrew.multistate.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JurisdictionTest {

    @Test
    void constructor_validInputs_exposesAllAccessors() {
        Jurisdiction subject = new Jurisdiction("CA", "California", JurisdictionKind.STATE);

        assertEquals("CA", subject.code());
        assertEquals("California", subject.displayName());
        assertEquals(JurisdictionKind.STATE, subject.kind());
    }

    @Test
    void constructor_nullCode_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Jurisdiction(null, "California", JurisdictionKind.STATE));
    }

    @Test
    void constructor_nullDisplayName_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Jurisdiction("CA", null, JurisdictionKind.STATE));
    }

    @Test
    void constructor_nullKind_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new Jurisdiction("CA", "California", null));
    }

    @Test
    void constructor_blankCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Jurisdiction("   ", "California", JurisdictionKind.STATE));
    }

    @Test
    void constructor_emptyDisplayName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Jurisdiction("CA", "", JurisdictionKind.STATE));
    }

    @Test
    void equals_sameFieldValues_areEqualAndShareHashCode() {
        Jurisdiction a = new Jurisdiction("CA", "California", JurisdictionKind.STATE);
        Jurisdiction b = new Jurisdiction("CA", "California", JurisdictionKind.STATE);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentKind_areNotEqual() {
        Jurisdiction state = new Jurisdiction("NY", "New York", JurisdictionKind.STATE);
        Jurisdiction city  = new Jurisdiction("NY", "New York", JurisdictionKind.CITY);

        assertNotEquals(state, city);
    }

    @Test
    void toString_containsCodeAndDisplayNameAndKind() {
        String rendered = new Jurisdiction("CA", "California", JurisdictionKind.STATE).toString();

        assertTrue(rendered.contains("CA"));
        assertTrue(rendered.contains("California"));
        assertTrue(rendered.contains("STATE"));
    }
}
