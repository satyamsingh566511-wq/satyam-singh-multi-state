# CLAUDE.md — Multi-State Tax Compliance Tracker

Project conventions for the uptimecrew Multi-State Tax Compliance Tracker. These rules govern every suggestion, refactor, and new file in this repo. They exist because we are modelling tax jurisdictions, residency boundaries, and dollar allocations — domains where silent precision loss or timezone drift causes real, audit-visible bugs.

## 1. Java baseline: JDK 17+

Target JDK 17 or newer. Use modern language features where they make the domain clearer: records for immutable value types (e.g. a `WorkDay` row), `var` for obvious local types, switch expressions, sealed types where the set of subtypes is closed (e.g. a fixed taxonomy of jurisdiction kinds). Do not pull in libraries that exist only to backfill pre-17 idioms.

## 2. Money: `BigDecimal`, scale 2, `RoundingMode.HALF_UP`

Every monetary amount — wages, allocations, withholdings, totals — is a `java.math.BigDecimal`. Always store and return values at `scale == 2`, rounded with `RoundingMode.HALF_UP`. Apply `setScale(2, RoundingMode.HALF_UP)` after any arithmetic that can produce more decimal places (division, percentage splits across jurisdictions). Never use `double`, `float`, `Double`, or `Float` for money — not in fields, parameters, return types, or intermediate calculations. Construct `BigDecimal` from `String` or `long`, never from a `double` literal (`new BigDecimal(0.1)` is broken; `new BigDecimal("0.10")` is correct).

## 3. Identifiers: `String` only

Every identifier in the domain — worker IDs, jurisdiction codes, work-day IDs, allocation IDs — is typed as `String`. Acceptable formats are UUID v4 (`"550e8400-e29b-41d4-a716-446655440000"`) or a prefixed synthetic ID (`"wkr_..."`, `"jur_..."`, `"day_..."`). Never use `int`, `long`, `Integer`, or `Long` for identifiers, even if a value happens to look numeric today; integer IDs leak ordering, expose row counts, and force painful migrations the moment we need to merge data sources.

## 4. Dates and times: `LocalDate` for calendar days, `Instant` for timestamps

Residency, day-count thresholds, and jurisdiction allocations are defined in **calendar days**, not moments in time — use `java.time.LocalDate` for any "what day did this happen" field. Use `java.time.Instant` only for true machine timestamps (audit log entries, record-created-at). Forbidden everywhere in this codebase: `java.util.Date`, `java.sql.Date`, `java.util.Calendar`, and timezone-naive `LocalDateTime` for points-in-time. If a date needs a time zone, pass the `ZoneId` explicitly — never rely on the JVM default.

## 5. Fields and classes: immutable by default, no Lombok `@Data`

Default every field to `private final`. Default every class to `final` unless it is explicitly designed for extension (and document why). Prefer constructor injection and records over mutable JavaBeans. Do **not** use Lombok `@Data`, `@Setter`, or `@AllArgsConstructor` — they silently generate mutators and `equals`/`hashCode` over every field, including ones that should never participate in identity. If boilerplate is heavy, use a `record`; if not, write the constructor and accessors explicitly so the reader can see exactly what is exposed.

## 6. Tests: JUnit 5 (Jupiter)

All tests use JUnit 5: `org.junit.jupiter.api.Test`, `Assertions.assertEquals`, `assertTrue`, `assertFalse`, `assertThrows`, `assertAll`, lifecycle hooks `@BeforeEach` / `@AfterEach`, parameterised tests via `@ParameterizedTest`. Do **not** import `org.junit.Test` (JUnit 4), `junit.framework.*`, or any Hamcrest/AssertJ DSL — Jupiter's built-in assertions are sufficient and keep the test surface uniform. Name tests `methodUnderTest_condition_expectedOutcome` so failures read as English sentences in CI.

## 7. Package root: `com.uptimecrew.multistate`

Every production and test class lives under the package root `com.uptimecrew.multistate`. Sub-packages should be domain-driven, not layer-driven — e.g. `com.uptimecrew.multistate.jurisdiction`, `com.uptimecrew.multistate.workday`, `com.uptimecrew.multistate.allocation` — rather than `...controller`, `...service`, `...dto`. Tests mirror the production package they cover.

---

## Domain context (for AI suggestions)

We are building the vocabulary for a Multi-State Tax Compliance Tracker: a system that records where a remote worker or travelling consultant physically worked each day, and allocates income to the correct jurisdiction at year end. Today's scope is the core types — what a **jurisdiction** is, what a **work day** is, and the **allocator** interface that splits income across jurisdictions. Every downstream feature (timeline UI, year-end allocation, audit export) will speak through these types, so they have to hold up under the conventions above.
