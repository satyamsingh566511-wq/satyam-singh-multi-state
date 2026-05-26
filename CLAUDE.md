# Multi-State Tax Compliance Tracker — Claude Guide

## What this app does
Records where a remote worker or travelling consultant physically worked each
day, and allocates income to the correct jurisdiction at year end. The core
vocabulary is a **Jurisdiction** (a taxing authority — state, city, country),
a **WorkDay** (one worker, one calendar day, one jurisdiction), and an
**Allocator** that splits annual income across jurisdictions based on those
work days. Every downstream feature — timeline UI, year-end allocation, audit
export — speaks through these types, so silent precision loss or timezone
drift here causes audit-visible bugs.

## How to run / test
- Build:  ./gradlew build
- Test:   ./gradlew test
- Run:    no runnable entry point yet — this is a domain library

## Domain rules — non-negotiable
- All monetary values use `BigDecimal` with `scale == 2`, `RoundingMode.HALF_UP`.
  Apply `setScale(2, RoundingMode.HALF_UP)` after any arithmetic that can
  produce more decimal places (division, percentage splits across jurisdictions).
- Never use `double`, `float`, `Double`, or `Float` for money — not in fields,
  parameters, return types, or intermediate calculations.
- Construct `BigDecimal` from `String` or `long`, never from a `double` literal.
  `new BigDecimal(0.1)` is broken; `new BigDecimal("0.10")` is correct.
- Identifiers are `String`. Acceptable formats: UUID v4 or a prefixed synthetic
  ID — `"wkr_..."` for workers, `"jur_..."` for jurisdictions, `"day_..."` for
  work days. Never `int`, `long`, `Integer`, or `Long` for identifiers.
- Calendar days use `java.time.LocalDate`. Machine timestamps (audit log,
  created-at) use `java.time.Instant`. Forbidden: `java.util.Date`,
  `java.sql.Date`, `java.util.Calendar`, and `LocalDateTime` for points-in-time.
- If a date needs a time zone, pass the `ZoneId` explicitly — never rely on the
  JVM default.

## Code style
- Java 17+. Records for immutable value types (e.g. a `WorkDay` row), `var` for
  obvious local types, switch expressions, sealed types where the subtype set
  is closed (e.g. a fixed taxonomy of jurisdiction kinds).
- Final classes by default. Final fields by default. No setters unless required.
- Prefer constructor injection and records over mutable JavaBeans.
- Package root is `com.uptimecrew.multistate`. Sub-packages are domain-driven,
  not layer-driven — `...jurisdiction`, `...workday`, `...allocation` — never
  `...controller`, `...service`, `...dto`.
- Forbid Lombok `@Data`, `@Setter`, `@AllArgsConstructor` — they silently
  generate mutators and `equals`/`hashCode` over every field. Use a `record`,
  or write the constructor and accessors explicitly.

## Testing
- JUnit 5 (Jupiter) only: `org.junit.jupiter.api.Test`, `Assertions.*`,
  `@BeforeEach` / `@AfterEach`, `@ParameterizedTest`.
- Do not import `org.junit.Test` (JUnit 4), `junit.framework.*`, or any
  Hamcrest/AssertJ DSL — Jupiter's built-in assertions are sufficient.
- One test class per production class. Tests mirror the production package.
- Test names: `methodUnderTest_condition_expectedOutcome` so CI failures read
  as English sentences.
- Always cover the exception path with `assertThrows`.

## Things to never put in this file
- API keys, tokens, passwords, worker PII, real wage data — environment
  variables and fixtures only.
