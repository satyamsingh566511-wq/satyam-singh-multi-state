-- db/queries/group_by_having.sql
-- Task 1.4 - GROUP BY + HAVING.
-- Run with:
--     psql -h localhost -U postgres -d postgres -f db/queries/group_by_having.sql

SET search_path TO multistate, public;

-- Only tenants that hold 2 or more allocations (i.e. income split across
-- multiple jurisdictions), with their per-tenant aggregates.
--
-- HAVING filters at the GROUP level, after aggregation, and its predicate
-- references an aggregate (COUNT). A row-level filter like
-- "WHERE a.amount > 0" could not express "groups with >= 2 rows", because at
-- WHERE time the groups do not exist yet. That is the WHERE-vs-HAVING line.
SELECT t.id            AS tenant_id,
       t.display_name,
       COUNT(a.id)     AS allocation_count,
       SUM(a.amount)   AS total_income,
       AVG(a.amount)   AS avg_income
FROM   multistate.tenant     t
JOIN   multistate.allocation a ON a.tenant_id = t.id
GROUP  BY t.id, t.display_name
HAVING COUNT(a.id) >= 2
ORDER  BY avg_income DESC;
