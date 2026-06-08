-- db/queries/joins.sql
-- Task 1.1 - JOINs across parent (tenant) and child (allocation).
-- Adapted to the Day 1 seeded schema: the parent table is multistate.tenant
-- and the child table is multistate.allocation (FK allocation.tenant_id ->
-- tenant.id). Tenants carry display_name/status; the money column is "amount".
-- Run with:
--     psql -h localhost -U postgres -d postgres -f db/queries/joins.sql

SET search_path TO multistate, public;

-- 1. INNER JOIN: every allocation paired with its owning tenant.
--    Rows with no match on EITHER side are dropped — so a tenant with no
--    allocations never appears here. That is the defining trait of INNER JOIN.
SELECT t.id              AS tenant_id,
       t.display_name,
       t.status,
       a.jurisdiction_code,
       a.amount,
       a.allocated_for
FROM   multistate.tenant     t
JOIN   multistate.allocation a ON a.tenant_id = t.id
ORDER  BY t.id, a.amount DESC;

-- 2. LEFT JOIN: every tenant, even those with zero allocations. Unmatched
--    tenants get NULLs on the allocation side; COUNT over the child key
--    counts only real matches, so childless parents report 0 (not 1).
SELECT t.id              AS tenant_id,
       t.display_name,
       COUNT(a.id)       AS allocation_count
FROM   multistate.tenant     t
LEFT  JOIN multistate.allocation a ON a.tenant_id = t.id
GROUP  BY t.id, t.display_name
ORDER  BY allocation_count DESC, t.id;
