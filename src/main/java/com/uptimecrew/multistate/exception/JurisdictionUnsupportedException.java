package com.uptimecrew.multistate.exception;

/**
 * Thrown when an allocation references a jurisdiction the system does not
 * support (unknown code, unregistered taxing authority).
 */
public final class JurisdictionUnsupportedException extends AllocationException {

    public JurisdictionUnsupportedException(String message) {
        super(message);
    }

    public JurisdictionUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
