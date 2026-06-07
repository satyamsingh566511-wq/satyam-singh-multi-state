-- V1__schema.sql — Multi-State Tax Compliance Tracker, initial schema.
--
-- Three tables in the `multistate` schema (never the default `public`):
--   jurisdiction — reference table of taxing authorities + income-tax flags
--   tenant       — primary table, one row per taxpayer entity
--   allocation   — computed income allocation per tenant + jurisdiction + period
--
-- Maps to the Week 1 Java domain: Jurisdiction(code, displayName, kind),
-- the worker/tenant identity, and IncomeAllocation(id, workerId, jurisdictionCode, amount, allocatedFor).

CREATE SCHEMA IF NOT EXISTS multistate;
SET search_path TO multistate, public;

-- ---------------------------------------------------------------------------
-- jurisdiction — reference table of taxing authorities (countries, states).
-- Created first: both other tables reference it.
-- ---------------------------------------------------------------------------
CREATE TABLE multistate.jurisdiction (
    -- intent: the stable natural code (e.g. 'US-CA') IS the identity. TEXT id,
    -- never SERIAL/BIGINT, so it maps straight onto IncomeAllocation.jurisdictionCode.
    code              TEXT PRIMARY KEY
                      CHECK (length(code) > 0),

    -- intent: a jurisdiction's display name is a natural key — two rows must not
    -- share a name, or audit exports and pickers become ambiguous.
    name              TEXT NOT NULL UNIQUE
                      CHECK (length(name) > 0),

    -- intent: closed taxonomy from JurisdictionKind. TEXT + CHECK, not native
    -- ENUM, so adding a kind is a one-line migration, not an ALTER TYPE.
    kind              TEXT NOT NULL
                      CHECK (kind IN ('COUNTRY', 'STATE', 'PROVINCE', 'CITY')),

    -- intent: drives allocation logic. A no-income-tax jurisdiction (TX, FL)
    -- must be representable explicitly, not inferred from a null rate.
    has_income_tax    BOOLEAN NOT NULL,

    -- intent: a marginal tax rate is a fraction in [0,1]. NUMERIC (never FLOAT)
    -- keeps it exact. Nullable: unknown or not-applicable for no-tax jurisdictions.
    top_marginal_rate NUMERIC(5,4)
                      CHECK (top_marginal_rate >= 0 AND top_marginal_rate <= 1),

    -- intent: a jurisdiction flagged no-income-tax must not carry a positive
    -- rate — stops the flag and the rate from silently contradicting each other.
    CONSTRAINT jurisdiction_rate_consistent_with_flag
        CHECK (has_income_tax OR COALESCE(top_marginal_rate, 0) = 0)
);

-- ---------------------------------------------------------------------------
-- tenant — primary table, one row per taxpayer entity (the Week 1 "worker").
-- ---------------------------------------------------------------------------
CREATE TABLE multistate.tenant (
    -- intent: TEXT id (prefixed synthetic 'wkr_'/'ten_' or UUID v4), never
    -- SERIAL — every identifier in this domain is an opaque string.
    id                          TEXT PRIMARY KEY
                                CHECK (length(id) > 0),

    -- intent: tenants are named for humans/entities; a blank name is meaningless.
    display_name                TEXT NOT NULL
                                CHECK (length(display_name) > 0),

    -- intent: the stable reference from the upstream HR/payroll system is the
    -- tenant's natural key — UNIQUE so the same entity is never double-loaded.
    external_ref                TEXT NOT NULL UNIQUE
                                CHECK (length(external_ref) > 0),

    -- intent: lifecycle state is a closed set — TEXT + CHECK, not native ENUM.
    status                      TEXT NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),

    -- intent: home/domicile state. Nullable — residency may be unknown at
    -- onboarding (defensible: a tenant exists before we know where they live).
    -- RESTRICT: jurisdiction is a reference table — you must not delete one out
    -- from under a tenant that points at it.
    residency_jurisdiction_code TEXT
                                REFERENCES multistate.jurisdiction(code) ON DELETE RESTRICT,

    -- intent: created-at is a machine instant — TIMESTAMPTZ in UTC, never naive
    -- TIMESTAMP (maps to java.time.Instant).
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- allocation — computed income allocation per tenant per jurisdiction per year.
-- Persisted form of Week 1 IncomeAllocation(id, workerId, jurisdictionCode,
-- amount, allocatedFor).
-- ---------------------------------------------------------------------------
CREATE TABLE multistate.allocation (
    -- intent: maps to IncomeAllocation.id — a TEXT surrogate id (not a composite
    -- PK) so each row carries the same stable identity as the Java record.
    id                TEXT PRIMARY KEY
                      CHECK (length(id) > 0),

    -- intent: owning tenant (IncomeAllocation.workerId). CASCADE — an allocation
    -- is meaningless without its tenant, so deleting the tenant deletes its
    -- allocations (strict parent-child).
    tenant_id         TEXT NOT NULL
                      REFERENCES multistate.tenant(id) ON DELETE CASCADE,

    -- intent: target jurisdiction (IncomeAllocation.jurisdictionCode). RESTRICT —
    -- jurisdiction is a reference table; you must not delete one that allocations
    -- still point at.
    jurisdiction_code TEXT NOT NULL
                      REFERENCES multistate.jurisdiction(code) ON DELETE RESTRICT,

    -- intent: money is NUMERIC(12,2) (scale 2, HALF_UP in Java), never FLOAT —
    -- silent precision loss here is audit-visible. amount >= 0: you cannot
    -- allocate a negative amount of income (mirrors IncomeAllocation's invariant).
    amount            NUMERIC(12,2) NOT NULL
                      CHECK (amount >= 0),

    -- intent: the calendar period the allocation is for — DATE (java.time.
    -- LocalDate from IncomeAllocation.allocatedFor), not a timestamp; the tax
    -- year is derivable from it.
    allocated_for     DATE NOT NULL,

    -- intent: audit insert time — TIMESTAMPTZ UTC. Not present in the Week 1 record.
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- intent: one allocation per tenant per jurisdiction per period — the domain's
    -- "per tenant per jurisdiction per year" rule; blocks duplicate splits.
    CONSTRAINT allocation_unique_per_tenant_jurisdiction_period
        UNIQUE (tenant_id, jurisdiction_code, allocated_for)
);
