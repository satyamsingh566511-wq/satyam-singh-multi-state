-- db/queries/cte.sql
-- Task 1.2 - Common Table Expression.
-- Run with:
--     psql -h localhost -U postgres -d postgres -f db/queries/cte.sql

SET search_path TO multistate, public;

-- A CTE named "totals" sums each tenant's allocated income, then the outer
-- SELECT joins it back to multistate.tenant to surface only the tenants whose
-- total crosses a threshold, alongside their human-readable name.
--
-- Why a CTE and not a subquery: the aggregate "income per tenant" is named
-- once and read once in the FROM clause, so the query reads top-down as a
-- pipeline (build totals -> join -> filter) instead of nesting the aggregation
-- inline. If a second query later needed the same per-tenant total, the CTE is
-- the seam to lift it out; a correlated subquery would have to be duplicated.

WITH totals AS (
    SELECT tenant_id   AS t_id,
           SUM(amount) AS total_income
    FROM   multistate.allocation
    GROUP  BY tenant_id
)
SELECT t.id            AS tenant_id,
       t.display_name,
       tot.total_income
FROM   multistate.tenant t
JOIN   totals            tot ON tot.t_id = t.id
WHERE  tot.total_income > 100000.00
ORDER  BY tot.total_income DESC;
