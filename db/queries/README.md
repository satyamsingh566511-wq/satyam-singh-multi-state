# SQL query catalogue

Four self-contained, read-only query files that demonstrate core SQL idioms
against the Day 1 schema (`multistate.tenant` parent, `multistate.allocation`
child, `multistate.jurisdiction` reference). Each file sets its own
`search_path` and runs cleanly with `psql -f`.

## Query catalogue

- **`joins.sql` — JOIN.** Answers "what income allocation does each tenant hold,
  and how many allocations does each tenant have (including tenants with none)?"
  Touches `multistate.tenant` and `multistate.allocation`. Uses an **INNER JOIN**
  to pair every allocation with its owning tenant (childless tenants drop out),
  then a **LEFT JOIN** to list every tenant with its allocation count, reporting
  `0` for any tenant that has no allocations.

- **`cte.sql` — CTE.** Answers "which tenants have total allocated income above a
  reporting threshold?" Touches `multistate.tenant` and `multistate.allocation`.
  A `WITH totals AS (...)` **common table expression** sums income per tenant,
  and the outer `SELECT` joins that named result back to `tenant` and filters to
  totals over `100000.00`.

- **`window.sql` — window function.** Answers "within each tenant, how does each
  allocation rank by amount, and what is the tenant's overall total?" Touches
  `multistate.tenant` and `multistate.allocation`. Uses
  `RANK() OVER (PARTITION BY tenant ORDER BY amount DESC)` and
  `SUM(amount) OVER (PARTITION BY tenant)` — **window functions** that annotate
  each allocation row without collapsing it.

- **`group_by_having.sql` — GROUP BY + HAVING.** Answers "which tenants split
  their income across two or more jurisdictions, and what are their per-tenant
  aggregates?" Touches `multistate.tenant` and `multistate.allocation`. Groups by
  tenant and uses a **HAVING** clause (`HAVING COUNT(*) >= 2`) to filter at the
  group level on an aggregate — something a row-level `WHERE` cannot express.

## Running locally

From the repository root, against a local Postgres (the schema lives in the
`multistate` schema, not `public`):

```bash
# 1. Apply the Day 1 schema, then the seed (jurisdiction -> tenant -> allocation).
psql -h localhost -U postgres -d postgres -f db/V1__schema.sql
psql -h localhost -U postgres -d postgres -f db/V2__seed.sql

# 2. Run any one of the four query files (swap in cte.sql / window.sql /
#    group_by_having.sql as desired).
psql -h localhost -U postgres -d postgres -f db/queries/joins.sql
```

`V2__seed.sql` intentionally ends with a negative-amount `INSERT` that the
`amount >= 0` CHECK rejects (and `ROLLBACK`s); the `ERROR` it prints is expected
and leaves no data behind.

## Running in tests

The integration test applies `V1__schema.sql` + `V2__seed.sql` and runs the
query files against a throwaway Postgres:

```bash
./gradlew test --tests "*QueryIT"
```

Testcontainers manages the container lifecycle automatically — it starts a
`postgres:16-alpine` container, waits for readiness, and tears it down at the end
of the run, so no manual `docker run` (or cleanup) is needed for the test path.

## Trade-offs

**CTE over a subquery in `cte.sql`.** The per-tenant income total is computed
once in a named `WITH totals AS (...)` block, so the outer query reads top-down
as a pipeline (build totals → join to `tenant` → filter) instead of nesting the
aggregation inside the `FROM`/`WHERE`. The name is the seam: if a second query
later needs the same per-tenant total it can be lifted out and reused, whereas a
correlated subquery would have to be duplicated and re-read at each reference
site. For this single-aggregate, single-threshold question the CTE is purely a
readability win — the planner inlines it — but it keeps the intent legible.

**Window function not replaceable with GROUP BY in `window.sql`.** A `GROUP BY`
collapses each tenant down to one aggregate row, destroying the individual
allocation rows. This query must emit *one row per allocation* while also
showing the tenant-level total and each allocation's rank within its tenant —
`SUM(...) OVER (PARTITION BY tenant)` and `RANK() OVER (...)` attach those
aggregates alongside every detail row without folding them away. Reproducing it
with `GROUP BY` would require a separate aggregate query joined back to the
detail rows; the window function expresses the same result in a single,
non-collapsing pass.
