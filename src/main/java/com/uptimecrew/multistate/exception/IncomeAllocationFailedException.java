package com.uptimecrew.multistate.exception;

/**
 * Thrown when income cannot be allocated across jurisdictions (e.g. no work
 * days recorded, totals that fail to reconcile, arithmetic preconditions violated).
 */
public final class IncomeAllocationFailedException extends AllocationException {

    public IncomeAllocationFailedException(String message) {
        super(message);
    }

    public IncomeAllocationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
