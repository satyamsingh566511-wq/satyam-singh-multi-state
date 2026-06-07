-- verify.sql — read-only checks against the seeded multistate schema.
-- Run after V1__schema.sql + V2__seed.sql:
--   psql -h localhost -U postgres -d multistate -f db/verify.sql

SET search_path TO multistate, public;

-- Q1 (JOIN, 3 tables): every allocation as a human-readable line —
-- which tenant was allocated how much in which jurisdiction, for which period.
\echo '== Q1: allocation detail (tenant x jurisdiction) =='
SELECT t.id              AS tenant,
       t.display_name    AS tenant_name,
       j.code            AS jurisdiction,
       j.name            AS jurisdiction_name,
       a.amount,
       a.allocated_for
FROM   multistate.allocation a
JOIN   multistate.tenant t       ON t.id   = a.tenant_id
JOIN   multistate.jurisdiction j ON j.code = a.jurisdiction_code
ORDER  BY t.id, a.amount DESC;

-- Q2 (GROUP BY + JOIN): total income allocated to each jurisdiction, with how
-- many allocations rolled up and whether that jurisdiction taxes income.
\echo '== Q2: total allocated income per jurisdiction =='
SELECT j.code,
       j.name,
       j.has_income_tax,
       COUNT(a.id)        AS allocation_count,
       SUM(a.amount)      AS total_allocated
FROM   multistate.jurisdiction j
JOIN   multistate.allocation a ON a.jurisdiction_code = j.code
GROUP  BY j.code, j.name, j.has_income_tax
ORDER  BY total_allocated DESC;

-- Q3 (GROUP BY): per-tenant summary — total income allocated and across how
-- many distinct jurisdictions (a multi-state worker shows count > 1).
\echo '== Q3: per-tenant allocation summary =='
SELECT t.id                                AS tenant,
       t.display_name                      AS tenant_name,
       COUNT(DISTINCT a.jurisdiction_code) AS jurisdictions,
       SUM(a.amount)                       AS total_allocated
FROM   multistate.tenant t
JOIN   multistate.allocation a ON a.tenant_id = t.id
GROUP  BY t.id, t.display_name
ORDER  BY total_allocated DESC;
