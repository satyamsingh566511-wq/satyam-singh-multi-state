package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.model.IncomeAllocation;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AllocationRegistry {

    private final Map<String, IncomeAllocation> byId;

    public AllocationRegistry(Collection<IncomeAllocation> allocations) {
        Objects.requireNonNull(allocations, "allocations");
        var copy = new LinkedHashMap<String, IncomeAllocation>();
        for (var allocation : allocations) {
            Objects.requireNonNull(allocation, "allocations must not contain null");
            var previous = copy.putIfAbsent(allocation.id(), allocation);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate allocation id: " + allocation.id());
            }
        }
        this.byId = Collections.unmodifiableMap(copy);
    }

    public int size() {
        return byId.size();
    }

    public Optional<IncomeAllocation> findById(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    public List<IncomeAllocation> findByJurisdictionAbove(String jurisdictionCode,
                                                          BigDecimal threshold) {
        Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
        Objects.requireNonNull(threshold, "threshold");
        return byId.values().stream()
                .filter(a -> a.jurisdictionCode().equals(jurisdictionCode)
                        && a.amount().compareTo(threshold) > 0)
                .sorted(Comparator.comparing(IncomeAllocation::amount).reversed()
                        .thenComparing(IncomeAllocation::id))
                .toList();
    }
}
