package com.uptimecrew.multistate.service;

import com.uptimecrew.multistate.entity.Allocation;
import com.uptimecrew.multistate.entity.Tenant;
import com.uptimecrew.multistate.exception.AllocationException;
import com.uptimecrew.multistate.graphql.LineItem;
import com.uptimecrew.multistate.model.IncomeAllocation;
import com.uptimecrew.multistate.model.WorkDay;
import com.uptimecrew.multistate.outbox.EventOutboxEntity;
import com.uptimecrew.multistate.outbox.EventOutboxRepository;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import com.uptimecrew.multistate.repository.AllocationRepository;
import com.uptimecrew.multistate.repository.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
/*
 * Not final: the @Transactional allocate(...) method requires Spring to create a
 * CGLIB proxy of this bean, which subclasses the target — impossible for a final
 * class. (The repo's "final by default" style yields to that framework constraint.)
 */
@Service
public class AllocationService {

    private static final Logger LOG = LoggerFactory.getLogger(AllocationService.class);

    static final String CACHE_NAME = "multistate.byId";

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final AllocationStrategy strategy;
    private final TenantRepository repository;
    private final TenantReadModelRepository readModelRepository;
    private final EventOutboxRepository outboxRepository;
    private final AllocationRepository allocationRepository;
    private final ObjectMapper objectMapper;

    public AllocationService(AllocationStrategy strategy,
                             TenantRepository repository,
                             TenantReadModelRepository readModelRepository,
                             EventOutboxRepository outboxRepository,
                             AllocationRepository allocationRepository,
                             ObjectMapper objectMapper) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.readModelRepository = Objects.requireNonNull(readModelRepository, "readModelRepository");
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.allocationRepository = Objects.requireNonNull(allocationRepository, "allocationRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
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
            /*
             * WARN on a known domain failure: log message + cause so the stack
             * trace renders, then rethrow so a higher layer decides recovery.
             */
            LOG.warn("strategy failed: {}", ex.getMessage(), ex);
            throw ex;
        }

        LOG.info("strategy={} returned allocations={}",
                strategy.getClass().getSimpleName(), allocations.size());

        List<IncomeAllocation> reconciled = reconcile(allocations, normalizedTotal);

        /* Persist the worker entity for this run inside the same transaction. */
        Tenant saved = repository.save(toTenant(workerId));
        LOG.info("persisted tenant id={}", saved.getId());

        /*
         * Write-through: project the just-saved JPA entity (plus the reconciled
         * allocations) into the Mongo read model so a later @Cacheable read path
         * can return the whole tree in one round-trip. Same id on both sides, so
         * a Mongo lookup and a Postgres lookup resolve the same logical tenant.
         */
        TenantReadModel projection = toReadModel(saved, reconciled);
        readModelRepository.save(projection);
        LOG.info("write-through to mongo id={} primaryState={}",
                projection.getId(), projection.getPrimaryState());

        /* Insert outbox entry for transactional event publishing. */
        try {
            String payload = objectMapper.writeValueAsString(reconciled);
            EventOutboxEntity outboxEntry = new EventOutboxEntity(workerId, "tenants.events", payload);
            outboxRepository.save(outboxEntry);
            LOG.info("outbox entry created id={}", outboxEntry.getId());
        } catch (Exception ex) {
            LOG.warn("failed to create outbox entry for workerId={}: {}", workerId, ex.toString());
            throw new RuntimeException("outbox creation failed", ex);
        }

        return reconciled;
    }

    /**
     * Read path fronted by Redis: {@code @Cacheable} short-circuits on a cache hit
     * before this body runs, so the INFO log below only fires on a miss. On a miss
     * we read the denormalised Mongo read model first (one round-trip for the whole
     * tree), falling back to a fresh projection rebuilt from the Postgres JPA entity
     * so a Mongo wipe doesn't break the read path.
     *
     * <p>{@code unless = "#result == null"} keeps a {@code null}/empty result out of
     * the cache. Spring evaluates the SpEL against the unwrapped {@link Optional}, so
     * a present {@code Optional} is cached and an {@link Optional#empty()} (returned
     * as {@code null} here) is not — a transient not-found never locks in.
     */
    @Cacheable(value = CACHE_NAME, unless = "#result == null")
    public Optional<TenantReadModel> findById(String id) {
        LOG.info("cache miss on id={}; reading from mongo", id);

        Optional<TenantReadModel> fromMongo = readModelRepository.findById(id);
        if (fromMongo.isPresent()) {
            return fromMongo;
        }

        /*
         * Fallback: rebuild the read-model projection from the JPA entity. The
         * entity's allocations are a LAZY @OneToMany not loaded outside a session,
         * so the rebuilt projection carries primaryState (residency code) but no
         * embedded allocations — the Mongo write-through is the authoritative copy.
         */
        return repository.findById(id)
                .map(e -> new TenantReadModel(
                        e.getId(), e.getResidencyJurisdictionCode(), Instant.now(), List.of()));
    }

    /**
     * Projects the saved {@link Tenant} and its reconciled allocations into the
     * denormalised Mongo {@link TenantReadModel}. {@code primaryState} is the
     * jurisdiction carrying the largest allocated amount (ties broken by the
     * first such line), which is the dimension the read model is {@code @Indexed}
     * on; it falls back to the tenant's residency code when there are no
     * allocations to rank.
     */
    private static TenantReadModel toReadModel(Tenant saved,
                                               List<IncomeAllocation> allocations) {
        Instant capturedAt = Instant.now();

        List<TenantReadModel.EmbeddedAllocation> embedded = new ArrayList<>(allocations.size());
        for (IncomeAllocation a : allocations) {
            embedded.add(new TenantReadModel.EmbeddedAllocation(
                    a.jurisdictionCode(), a.amount(), a.allocatedFor(), capturedAt));
        }

        String primaryState = allocations.stream()
                .max((a, b) -> a.amount().compareTo(b.amount()))
                .map(IncomeAllocation::jurisdictionCode)
                .orElse(saved.getResidencyJurisdictionCode());

        return new TenantReadModel(saved.getId(), primaryState, capturedAt, embedded);
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
     *
     * <p>Status is {@code ACTIVE} — one of the values the {@code tenant_status_check}
     * constraint allows ({@code ACTIVE/INACTIVE/SUSPENDED}); "ALLOCATED" is not a
     * tenant lifecycle state and the schema rejects it. Residency is left null: the
     * allocation jurisdictions are not guaranteed to exist in the {@code jurisdiction}
     * reference table, and {@code residency_jurisdiction_code} is a RESTRICT foreign
     * key, so attributing one here would risk a constraint violation.
     */
    private static Tenant toTenant(String workerId) {
        return new Tenant(workerId, workerId, workerId, "ACTIVE", null, Instant.now());
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

        /*
         * Indices ordered by amount descending, ties broken by original position
         * so the distribution is deterministic.
         */
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

    /** Most-recently-projected tenants, capped at {@code limit}, for the GraphQL latestTenants query. */
    public List<TenantReadModel> findLatest(int limit) {
        return readModelRepository.findAll(PageRequest.of(0, limit)).getContent();
    }

    /**
     * Batch loader behind the GraphQL {@code tenant.lines} {@code @BatchMapping}.
     * Given every parent tenant in a single generation, it issues ONE
     * {@code WHERE tenant_id IN (...)} SELECT for all their line items and groups
     * the rows back under their owning parent — the fix for the classic N+1 where
     * resolving {@code lines} on N tenants would otherwise fire N separate SELECTs.
     *
     * <p>The returned map is keyed by the parent {@link TenantReadModel} object
     * itself (not its id): Spring for GraphQL matches each source object against
     * this map by identity to resolve its {@code lines} field. Every parent gets an
     * entry — empty list when it has no line items — so the non-null
     * {@code [LineItem!]!} schema contract always holds.
     *
     * <p>{@code @Transactional(readOnly = true)}: open-in-view is disabled, so the
     * read must run inside a transaction for the query to execute; only the FK id
     * ({@code allocation.getTenant().getId()}) is touched, which the LAZY proxy
     * answers without a second select.
     */
    @Transactional(readOnly = true)
    public Map<TenantReadModel, List<LineItem>> loadLineItemsByParent(List<TenantReadModel> parents) {
        Map<String, TenantReadModel> parentsById = new HashMap<>();
        for (TenantReadModel parent : parents) {
            parentsById.put(parent.getId(), parent);
        }

        Map<TenantReadModel, List<LineItem>> result = new HashMap<>();
        for (TenantReadModel parent : parents) {
            result.put(parent, new ArrayList<>());
        }

        /* ONE SELECT for the whole generation of parents. */
        List<Allocation> rows = allocationRepository.findByTenant_IdIn(parentsById.keySet());
        LOG.info("graphql @BatchMapping lines: {} parents -> {} line items in one query",
                parents.size(), rows.size());

        for (Allocation a : rows) {
            TenantReadModel parent = parentsById.get(a.getTenant().getId());
            if (parent != null) {
                result.get(parent).add(new LineItem(
                        a.getId(), a.getJurisdictionCode(), a.getAmount().floatValue()));
            }
        }
        return result;
    }
}
