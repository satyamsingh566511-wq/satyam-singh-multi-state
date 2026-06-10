package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.entity.Tenant;
import com.uptimecrew.multistate.exception.AllocationException;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;
import com.uptimecrew.multistate.repository.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Application-facing entry point for year-end income allocation. It owns no
 * splitting logic of its own — the {@link AllocationStrategy} is injected, never
 * constructed here — so the same service can run a day-count, income-weighted,
 * or hybrid split purely by what the caller wires in.
 *
 * <p>Spring owns this bean's lifecycle ({@code @Service}). With a single
 * constructor, Spring 6 injects it without any field-level wiring annotation; it
 * supplies the {@code @Primary} {@link AllocationStrategy} bean (or a
 * {@code @Qualifier}-named one), so the {@code new}-the-strategy wiring never
 * appears in production code.
 */
@Service
public final class AllocationService {

    private static final Logger LOG = LoggerFactory.getLogger(AllocationService.class);

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final AllocationStrategy strategy;
    private final TenantRepository repository;

    public AllocationService(AllocationStrategy strategy, TenantRepository repository) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.repository = Objects.requireNonNull(repository, "repository");
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
     *
     * <p>{@code @Transactional}: the strategy invocation and the
     * {@link TenantRepository#save(Object)} that records the run share one
     * transaction, so a persistence failure rolls the whole unit back rather
     * than leaving a half-written {@link Tenant}.
     */
    @Transactional
    public List<IncomeAllocation> allocate(String workerId,
                                           BigDecimal totalIncome,
                                           List<WorkDay> workDays,
                                           LocalDate allocatedFor) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(totalIncome, "totalIncome");
        Objects.requireNonNull(workDays, "workDays");
        Objects.requireNonNull(allocatedFor, "allocatedFor");

        BigDecimal normalizedTotal = totalIncome.setScale(2, RoundingMode.HALF_UP);

        LOG.info("invoking strategy={} for workerId={} total={} workDays={}",
                strategy.getClass().getSimpleName(), workerId, normalizedTotal,
                workDays.size());

        List<IncomeAllocation> allocations;
        try {
            allocations = strategy.allocate(workerId, normalizedTotal, workDays, allocatedFor);
        } catch (AllocationException ex) {
            // WARN on a known domain failure: log message + cause so the stack
            // trace renders, then rethrow so a higher layer decides recovery.
            LOG.warn("strategy failed: {}", ex.getMessage(), ex);
            throw ex;
        }

        LOG.info("strategy={} returned allocations={}",
                strategy.getClass().getSimpleName(), allocations.size());

        List<IncomeAllocation> reconciled = reconcile(allocations, normalizedTotal);

        // Persist the worker entity for this run inside the same transaction.
        Tenant saved = repository.save(toTenant(workerId, reconciled));
        LOG.info("persisted tenant id={}", saved.getId());

        return reconciled;
    }

    /**
     * Enforces the audit invariant that the allocated amounts sum exactly to
     * {@code normalizedTotal}, distributing any rounding residual one cent at a
     * time. Returns the input list unchanged when it is empty or already
     * reconciles.
     */
    private static List<IncomeAllocation> reconcile(List<IncomeAllocation> allocations,
                                                    BigDecimal normalizedTotal) {
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
     * Builds the primary {@link Tenant} entity recording this allocation run.
     * Residency is taken from the jurisdiction carrying the largest allocated
     * amount (the worker's dominant jurisdiction), or left null when there are
     * no allocations to attribute.
     */
    private static Tenant toTenant(String workerId, List<IncomeAllocation> allocations) {
        String residency = allocations.stream()
                .max(Comparator.comparing(IncomeAllocation::amount))
                .map(IncomeAllocation::jurisdictionCode)
                .orElse(null);
        return new Tenant(workerId, workerId, workerId, "ALLOCATED", residency, Instant.now());
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
