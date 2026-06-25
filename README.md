# Multistate

A Multi-State Tax Compliance Tracker — a Java domain library for recording where
a remote worker or travelling consultant physically worked each day, and for
allocating their annual income to the correct taxing jurisdictions at year end.

## What it does

Remote and travelling workers earn income across many tax jurisdictions in a
single year. To file correctly, that income must be split across the states,
cities, or countries where the work physically happened. This library models
that problem with three core types:

- **Jurisdiction** — a taxing authority: a state, city, or country.
- **WorkDay** — one worker, one calendar day, one jurisdiction. The atomic
  record of where work happened.
- **IncomeAllocation** — the share of annual income assigned to one
  jurisdiction, the auditable output of the year-end calculation.

Income is split by an injected **`AllocationStrategy`**, so the same
`AllocationService` can run different policies without changing:

- `byDayCount()` — split evenly by the number of days worked in each jurisdiction.
- `weightedByIncome(weights)` — split by `daysWorked × weight`, reflecting each
  jurisdiction's earning power.
- `blendOf(primary, secondary, primaryWeight)` — compose two strategies, weighting
  `primary` by `primaryWeight` and `secondary` by its complement.

Because tax filings are audited, correctness is non-negotiable. All money is
`BigDecimal` at scale 2 (`HALF_UP`) — never `double`/`float` — and dates use
`java.time.LocalDate` so there is no timezone drift. A proportional split rounded
to cents almost never sums back to the exact total, so `AllocationService`
distributes the leftover residual one cent at a time across allocations (largest
first). This guarantees the allocated amounts reconcile *exactly* to the
total — the first invariant an auditor checks.

## Build & test

Requires a JDK 17+ toolchain; the Gradle wrapper handles everything else.

```sh
# Compile and run the full build (compile + test)
./gradlew build

# Run the test suite only
./gradlew test

# Run a single test class
./gradlew test --tests "com.uptimecrew.multistate.service.AllocationServiceMockitoTest"

# Boot the Spring Boot service (defaults to the `local` profile, port 8080)
./gradlew bootRun

# Once running, check liveness via the Actuator health endpoint
curl http://localhost:8080/actuator/health
```

The allocation domain (model + strategies) is still a self-contained library,
but as of Week 2 Day 3 it is wrapped in a bootable Spring Boot service
(`Application.java`) exposing the Actuator `health` and `info` endpoints.

As of Week 2 Day 5, the persistence stack is exercised end to end by `TenantPolyglotIT`, a `@SpringBootTest` that boots against real Postgres, Mongo, and Redis containers via Testcontainers to verify the write-through and Redis cache paths.

As of Week 3 Day 1, the service is secured with Spring Security 7 as an OAuth2 Resource Server (JWT), with a `@PreAuthorize`-guarded tenant controller, a Bucket4j rate-limited LLM summary endpoint, and security tests covering mocked-JWT access and rate-limit exhaustion.

As of Week 3 Day 3, the service implements the transactional outbox pattern with a Kafka consumer that projects domain events into a Mongo read model. The outbox query uses pessimistic locking (`FOR UPDATE SKIP LOCKED`) to prevent duplicate publishing, and the consumer is idempotent — replayed events overwrite allocations at the same (jurisdictionCode, allocatedFor) coordinates rather than duplicating them.

As of Week 3 Day 4, the read model is exposed over GraphQL via Spring for GraphQL (`schema.graphqls`): a `TenantGraphQlController` wires `tenant(id)` and `latestTenants(limit)` queries plus a `summarizeTenant(id)` mutation. The `tenant.lines` field is resolved with an `@BatchMapping` that loads every parent's line items in a single `WHERE tenant_id IN (...)` query, eliminating the N+1. The mutation produces an LLM `TenantSummary` through Spring AI structured-output binding (`.entity(TenantSummary.class)`), then re-validates it against a hand-written JSON Schema (`schemas/TenantSummary.schema.json`) so a drifting model payload fails loudly instead of shipping a malformed summary. `TenantGraphQlIT` exercises all three legs against real Postgres, Mongo, and Redis containers with `@AutoConfigureGraphQlTester`.

As of Week 3 Day 5, the service is instrumented for distributed tracing with OpenTelemetry. The `opentelemetry-spring-boot-starter` auto-instruments HTTP, JDBC, and the scheduled outbox poll, exporting OTLP/HTTP to a Jaeger collector; the `opentelemetry-spring-kafka-2.7` instrumentation propagates the W3C `traceparent` header so a Kafka producer span and its consumer span share one trace id (a `TraceparentLoggingProducerListener` under `kafka/` logs the outgoing header as a no-Jaeger smoke check). `LlmSummaryService` wraps the Spring AI `ChatClient` call in a manual `llm.summarize` CLIENT span carrying `llm.model`, `llm.input.aggregate_id`, and `llm.tokens.in`/`llm.tokens.out` cost attributes (set after the call returns but before `span.end()`, with `recordException` + `ERROR` status on failure). `TenantObservabilityIT` proves trace continuity in-process: it overrides the `OpenTelemetry` bean with an SDK whose only exporter is an `InMemorySpanExporter` (via `SimpleSpanProcessor`, so finished spans are readable immediately) and asserts — against five Testcontainers (Postgres, Mongo, Redis, Kafka, and Jaeger as a `GenericContainer`) — that an HTTP request emits a server span with a JDBC child, that the outbox → Kafka → consumer → Mongo chain rides a single trace id, and that the `llm.summarize` span carries its token attributes. A small `tags: [String!]!` field and `tenantsByTag(tag)` query were shipped through a three-agent workflow (generator → tester → reviewer). OpenTelemetry is pinned to `2.28.1` / `2.28.1-alpha` (core `1.62.0`) — the minimum compatible with Spring Boot 4.0.6, since the `2.10.0` line references a Boot 3 Kafka class removed in Boot 4.

As of Week 4 Day 1, the repo also contains `multistate-web/`, a Vite + React 19 + TypeScript front end (the first UI for the tracker). It renders a tenant detail page that reads tenant data through a `useTenant` hook and demonstrates lifted state: a `TenantDetailPage` owns a `threshold` value that a controlled `ThresholdSlider` mutates and a sibling `ThresholdReadout` reads. Routing is a hand-rolled hash router off `window.location.hash` (TanStack Router lands W4 D3). Strict TypeScript, ESLint 9, and a Vitest smoke test guard the build; CI runs them via a GitHub Action.

```sh
cd multistate-web
pnpm install         # Node 20 (see .nvmrc); uses pnpm (pinned via packageManager)
pnpm dev             # Vite dev server on http://localhost:5173
```

Then open the tenant page directly at <http://localhost:5173/#/tenants/stub-id-1>. Other scripts: `pnpm build`, `pnpm lint`, `pnpm typecheck`, `pnpm test`.

As of Week 4 Day 2, `multistate-web/` grows three distinct state-management patterns, each matched to its job. (1) The tenant page is now a `useReducer` state machine: `detailReducer` (`TenantDetailPage.reducer.ts`) owns every transition across an `idle → loading → success | empty | error` discriminated union, with a `never` exhaustiveness check so a new action variant fails to compile until handled — the component only dispatches and reads `state.status`, letting TypeScript narrow each render branch. (2) Cross-cutting filter state moves into a Zustand store (`useTenantFilterStore`) composed with the `devtools` and `persist` middleware; each `FilterStrip` control subscribes to only its own slice (a keystroke in search re-renders just `SearchControl`), and `partialize` persists *only* `threshold` to `localStorage` — search text and chips are deliberately session-only so a stale query doesn't resurface on reload. The store resolves a safe in-memory storage fallback when `localStorage` is absent (jsdom/SSR) so `set()` never throws. (3) A `useDebouncedSearch` hook lags the store's `searchText` by 300ms, clearing its timer on every keystroke and on unmount so a pending timer never fires after the component is gone. An `ErrorBoundary` (class component, `getDerivedStateFromError` + `componentDidCatch`) wraps the route in `App.tsx` with a render-prop fallback and a retry that re-mounts the subtree; a DEV-only "Trigger error" button flips a flag that throws during render (boundaries catch render errors, not event-handler errors) to exercise it. Vitest unit tests cover the reducer transitions, the store actions/persistence, and the debounce hook's timer cleanup.

## Project layout

Packages are domain-driven, rooted at `com.uptimecrew.multistate`:

- `model/` — immutable value types: `Jurisdiction`, `WorkDay`, `IncomeAllocation`.
- `service/` — allocation engine: `AllocationService`, the `AllocationStrategy`
  implementations, and the `AllocationStrategies` factory.
- `db/` — Postgres schema (`V1__schema.sql`), transactional seed (`V2__seed.sql`),
  and verification queries (`verify.sql`); see [db/README.md](db/README.md).

Tests use JUnit 5 (Jupiter) and Mockito, mirror the production package layout,
and follow the `methodUnderTest_condition_expectedOutcome` naming convention.

## AI reflection

I used Claude Code as a pair-programming partner on this project, and the
experience reshaped how I divide work between myself and the tool. It was most
valuable for mechanical breadth — scaffolding parallel test classes, keeping
naming conventions consistent, and surfacing the exact spots where a `double`
might sneak into a money calculation. Where I had to stay firmly in control was
the domain reasoning: the AI happily generates plausible-looking allocation code,
but the rounding-residual invariant (that cents must reconcile *exactly* to the
total) is the kind of correctness guarantee that only a human who understands the
audit requirement can specify and verify. My most useful habit was treating every
AI suggestion as a draft to be challenged — reading the generated `BigDecimal`
arithmetic line by line, writing a focused test that forces a known residual, and
confirming the sum invariant held rather than trusting that it "looked right."
The lesson I am taking forward is that AI accelerates the typing, not the
thinking: it is a force multiplier for engineers who already know what correct
looks like, and a liability for anyone who outsources that judgement to it.
