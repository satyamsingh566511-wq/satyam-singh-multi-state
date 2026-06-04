package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application-facing entry point for year-end income allocation. It owns no
 * splitting logic of its own — the {@link AllocationStrategy} is injected, never
 * constructed here — so the same service can run a day-count, income-weighted,
 * or hybrid split purely by what the caller wires in.
 */
public final class AllocationService {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final AllocationStrategy strategy;

    public AllocationService(AllocationStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    /**
     * Allocates {@code totalIncome} across the jurisdictions implied by
     * {@code workDays}, delegating the split to the injected strategy.
     * Pre-processing normalise the incoming total to the canonical money
     * scale before any split occurs. Post-processing enforces the audit
     * invariant that the allocated amounts reconcile back to that total — a
     * proportional split rounded to cents almost always leaves a residual of a
     * few cents, so the leftover is distributed one cent at a time across the
     * allocations (largest amount first) rather than dumped on a single line or
     * silently dropped. The result is guaranteed to sum exactly to the
     * normalised total, which is what an auditor checks first.
     */
    public List<IncomeAllocation> allocate(String workerId,
                                           BigDecimal totalIncome,
                                           List<WorkDay> workDays,
                                           LocalDate allocatedFor) {
        Objects.requireNonNull(totalIncome, "totalIncome");

        BigDecimal normalizedTotal = totalIncome.setScale(2, RoundingMode.HALF_UP);

        List<IncomeAllocation> allocations =
                strategy.allocate(workerId, normalizedTotal, workDays, allocatedFor);

        if (allocations.isEmpty()) {
            return allocations;
        }

        BigDecimal allocated = allocations.stream()
                .map(IncomeAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal residual = normalizedTotal.subtract(allocated);
        if (residual.signum() == 0) {
            return allocations;
        }

        return List.copyOf(distributeResidual(allocations, residual));
    }

    /**
     * Spreads {@code residual} (a whole number of cents, positive or negative)
     * across the allocations one cent at a time, handing each cent to the
     * allocation with the next-largest amount. Distributing rather than dumping
     * the whole residual on one line keeps each jurisdiction's rounding
     * distortion to a single cent, and ordering by amount makes that cent the
     * least significant relative to the line it lands on. The original list
     * order is preserved; only amounts change.
     */
    private static List<IncomeAllocation> distributeResidual(List<IncomeAllocation> allocations,
                                                             BigDecimal residual) {
        int pennies = residual.movePointRight(2).intValueExact();
        BigDecimal step = pennies > 0 ? CENT : CENT.negate();
        int remaining = Math.abs(pennies);

        // Indices ordered by amount descending, ties broken by original position
        // so the distribution is deterministic.
        Integer[] order = new Integer[allocations.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int byAmount = allocations.get(b).amount().compareTo(allocations.get(a).amount());
            return byAmount != 0 ? byAmount : Integer.compare(a, b);
        });

        BigDecimal[] amounts = new BigDecimal[allocations.size()];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = allocations.get(i).amount();
        }
        for (int k = 0; remaining > 0; k++, remaining--) {
            int idx = order[k % order.length];
            amounts[idx] = amounts[idx].add(step);
        }

        List<IncomeAllocation> result = new ArrayList<>(allocations.size());
        for (int i = 0; i < allocations.size(); i++) {
            IncomeAllocation original = allocations.get(i);
            if (amounts[i].compareTo(original.amount()) == 0) {
                result.add(original);
            } else {
                result.add(new IncomeAllocation(
                        original.id(),
                        original.workerId(),
                        original.jurisdictionCode(),
                        amounts[i],
                        original.allocatedFor()));
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AllocationService other)) return false;
        return strategy.equals(other.strategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy);
    }

    @Override
    public String toString() {
        return "AllocationService{strategy=" + strategy + "}";
    }
}
