-- V2__seed.sql — synthetic seed data for the Multi-State Tax Compliance Tracker.
--
-- The whole seed runs as ONE transaction: if any INSERT fails, every prior
-- INSERT in this block rolls back, so the database is never left half-seeded.
-- Insert order respects FKs: jurisdiction (reference) -> tenant -> allocation.

BEGIN;

-- Reference table first: tenants and allocations both point at it.
INSERT INTO multistate.jurisdiction (code, name, kind, has_income_tax, top_marginal_rate) VALUES
    ('US-CA',     'California',     'STATE',   TRUE,  0.1330),
    ('US-NY',     'New York',       'STATE',   TRUE,  0.1090),
    ('US-OR',     'Oregon',         'STATE',   TRUE,  0.0990),
    ('US-TX',     'Texas',          'STATE',   FALSE, NULL),
    ('US-WA',     'Washington',     'STATE',   FALSE, NULL),
    ('US-NY-NYC', 'New York City',  'CITY',    TRUE,  0.0388);

-- Tenants (taxpayer entities). residency_jurisdiction_code is nullable:
-- tenant-e's residency is intentionally unknown to exercise the optional FK.
INSERT INTO multistate.tenant (id, display_name, external_ref, status, residency_jurisdiction_code) VALUES
    ('tenant-a', 'Example Tenant Alpha',   'hr-2026-0001', 'ACTIVE',    'US-CA'),
    ('tenant-b', 'Example Tenant Bravo',   'hr-2026-0002', 'ACTIVE',    'US-TX'),
    ('tenant-c', 'Example Tenant Charlie', 'hr-2026-0003', 'ACTIVE',    'US-CA'),
    ('tenant-d', 'Example Tenant Delta',   'hr-2026-0004', 'INACTIVE',  'US-WA'),
    ('tenant-e', 'Example Tenant Echo',    'hr-2026-0005', 'SUSPENDED', NULL);

-- Allocations: income split per tenant per jurisdiction for tax year 2025.
-- Each row is unique on (tenant_id, jurisdiction_code, allocated_for).
-- tenant-a split CA/NY; the others single-jurisdiction.
INSERT INTO multistate.allocation (id, tenant_id, jurisdiction_code, amount, allocated_for) VALUES
    ('doc-2026-0001', 'tenant-a', 'US-CA',      80000.00, DATE '2025-12-31'),
    ('doc-2026-0002', 'tenant-a', 'US-NY',      45000.00, DATE '2025-12-31'),
    ('doc-2026-0003', 'tenant-b', 'US-TX',     120000.00, DATE '2025-12-31'),
    ('doc-2026-0004', 'tenant-c', 'US-CA',      95000.00, DATE '2025-12-31'),
    ('doc-2026-0005', 'tenant-d', 'US-WA',      70000.00, DATE '2025-12-31'),
    ('doc-2026-0006', 'tenant-e', 'US-NY-NYC',  60000.00, DATE '2025-12-31');

COMMIT;

-- ---------------------------------------------------------------------------
-- Intentional failure test — proves the amount >= 0 CHECK
-- (allocation_amount_check) rejects negative income, and leaves no data behind.
--
-- Wrapped in a DO block that traps the check_violation: a bare
-- `INSERT ... -1.00` raises an error that aborts the whole script when this file
-- is executed as a single JDBC batch (every Testcontainers IT applies it that
-- way via Statement.execute(Files.readString(...))). Catching the violation here
-- keeps the demonstration — the constraint still fires and is asserted — without
-- propagating a fatal error to the JDBC driver. Nothing is committed.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    INSERT INTO multistate.allocation (id, tenant_id, jurisdiction_code, amount, allocated_for)
        VALUES ('doc-2026-9999', 'tenant-a', 'US-CA', -1.00, DATE '2025-12-31');
    RAISE EXCEPTION 'allocation_amount_check did NOT reject a negative amount';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'allocation_amount_check correctly rejected negative amount -1.00';
END $$;
