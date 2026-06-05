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
```

There is no runnable entry point — this is a domain library, consumed by other
modules (timeline UI, year-end allocation, audit export).

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
