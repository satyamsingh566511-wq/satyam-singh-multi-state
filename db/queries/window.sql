-- db/queries/window.sql
-- Task 1.3 - Window function.
-- Run with:
--     psql -h localhost -U postgres -d postgres -f db/queries/window.sql

SET search_path TO multistate, public;

-- For each tenant, RANK that tenant's allocations by amount (highest = 1) and
-- attach the tenant's total income on the same row. The hallmark of a window
-- function: rows are NOT collapsed. Every allocation still produces one output
-- row; the PARTITION BY just scopes the rank/sum to its tenant. Contrast with
-- GROUP BY, which would fold each tenant down to a single aggregate row.
SELECT t.id            AS tenant_id,
       t.display_name,
       a.jurisdiction_code,
       a.amount,
       RANK()      OVER (PARTITION BY t.id ORDER BY a.amount DESC) AS amount_rank,
       SUM(a.amount) OVER (PARTITION BY t.id)                       AS tenant_total
FROM   multistate.tenant     t
JOIN   multistate.allocation a ON a.tenant_id = t.id
ORDER  BY t.id, amount_rank;
