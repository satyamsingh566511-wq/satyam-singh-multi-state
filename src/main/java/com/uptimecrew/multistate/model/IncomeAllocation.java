package com.uptimecrew.multistate.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public record IncomeAllocation(String id,
                               String workerId,
                               String jurisdictionCode,
                               BigDecimal amount,
                               LocalDate allocatedFor) {

    public IncomeAllocation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(allocatedFor, "allocatedFor");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (jurisdictionCode.isBlank()) {
            throw new IllegalArgumentException("jurisdictionCode must not be blank");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
