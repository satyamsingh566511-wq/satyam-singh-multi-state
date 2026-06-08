package com.uptimecrew.multistate.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test (IT suffix — runs against a real Postgres, not a mock) that
 * exercises the Task 1 SQL idioms against the Day 1 schema + seed inside a
 * Testcontainers-managed {@code postgres:16-alpine}.
 *
 * <p>The container field is {@code static} so a single Postgres is started once
 * and reused by every test in the class — declaring it per-instance would pay
 * the ~2-3s startup cost on every {@code @Test}. {@link TestInstance.Lifecycle#PER_CLASS}
 * lets {@link #applySchemaAndSeed()} be a non-static {@code @BeforeAll}.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantQueryIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    void applySchemaAndSeed() throws Exception {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {

            // V1 is pure DDL — applies cleanly.
            stmt.execute(Files.readString(Path.of("db/V1__schema.sql")));

            // V2 commits the good seed, then deliberately attempts a negative
            // amount to prove the `amount >= 0` CHECK bites. psql swallows that
            // (no ON_ERROR_STOP) and ROLLBACKs it; over JDBC the multi-statement
            // script raises the violation AFTER the good rows have COMMITted, so
            // we expect exactly that check violation here and let it pass.
            try {
                stmt.execute(Files.readString(Path.of("db/V2__seed.sql")));
            } catch (SQLException expected) {
                if (!"23514".equals(expected.getSQLState())) {
                    throw expected; // 23514 = check_violation; anything else is real
                }
            }
        }
    }

    @Test
    void cteQuery_sumsIncomePerTenant_returnsOnlyTenantsAboveThreshold() throws Exception {
        var rows = runQuery("db/queries/cte.sql", rs -> new TenantTotal(
                rs.getString("tenant_id"),
                rs.getString("display_name"),
                rs.getBigDecimal("total_income")));

        // Seed: only tenant-a (125000) and tenant-b (120000) clear the 100000
        // threshold, and the query orders by total income DESC.
        assertThat(rows)
                .as("CTE result rows for seeded tenants above the income threshold")
                .isNotEmpty()
                .hasSize(2)
                .extracting(TenantTotal::tenantId)
                .containsExactly("tenant-a", "tenant-b");

        assertThat(rows).allSatisfy(r ->
                assertThat(r.total()).isGreaterThan(new BigDecimal("100000.00")));
    }

    @Test
    void windowQuery_doesNotCollapseRows_ranksAllocationsWithinEachTenant() throws Exception {
        var rows = runQuery("db/queries/window.sql", rs -> new WindowRow(
                rs.getString("tenant_id"),
                rs.getString("jurisdiction_code"),
                rs.getBigDecimal("amount"),
                rs.getInt("amount_rank"),
                rs.getBigDecimal("tenant_total")));

        // The hallmark of a window function: one output row per input row. There
        // are 6 seeded allocations, so 6 rows survive (GROUP BY would collapse them).
        assertThat(rows)
                .as("window function keeps one row per seeded allocation")
                .hasSize(6);

        // tenant-a is split across two jurisdictions; both rows carry the same
        // windowed SUM, and the larger amount (US-CA, 80000) ranks 1.
        assertThat(rows)
                .filteredOn(r -> r.tenantId().equals("tenant-a"))
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.tenantTotal()).isEqualByComparingTo("125000.00"));

        assertThat(rows)
                .filteredOn(r -> r.tenantId().equals("tenant-a") && r.amountRank() == 1)
                .extracting(WindowRow::jurisdictionCode)
                .containsExactly("US-CA");
    }

    @Test
    void groupByHavingQuery_filtersAtGroupLevel_keepsOnlyMultiJurisdictionTenants() throws Exception {
        var rows = runQuery("db/queries/group_by_having.sql", rs -> new GroupRow(
                rs.getString("tenant_id"),
                rs.getLong("allocation_count"),
                rs.getBigDecimal("avg_income")));

        // HAVING COUNT(*) >= 2 is a group-level filter: only tenant-a holds more
        // than one allocation, so it is the single surviving group.
        assertThat(rows)
                .as("groups surviving HAVING COUNT(*) >= 2")
                .hasSize(1)
                .first()
                .satisfies(r -> {
                    assertThat(r.tenantId()).isEqualTo("tenant-a");
                    assertThat(r.allocationCount()).isEqualTo(2L);
                    assertThat(r.avgIncome()).isEqualByComparingTo("62500.00");
                });
    }

    @Test
    void insertingNegativeAmount_violatesAmountCheck_throws() throws Exception {
        // Exception path: the schema's `amount >= 0` CHECK must reject negative
        // income with SQLSTATE 23514 (check_violation).
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {

            assertThatThrownBy(() -> stmt.executeUpdate(
                    "INSERT INTO multistate.allocation "
                  + "(id, tenant_id, jurisdiction_code, amount, allocated_for) "
                  + "VALUES ('doc-neg-001', 'tenant-a', 'US-CA', -1.00, DATE '2025-12-31')"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(t ->
                            assertThat(((SQLException) t).getSQLState()).isEqualTo("23514"));
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Opens a JDBC connection to the seeded container. Uses the container's own
     * {@code createConnection}, which retries until the database is reachable —
     * this absorbs the brief startup race on VM-backed Docker runtimes (e.g.
     * Rancher Desktop) where the host-side port forward is established
     * asynchronously, just after the in-container "ready" log the default wait
     * strategy keys on.
     */
    private Connection openConnection() throws SQLException {
        return PG.createConnection("");
    }

    /**
     * Runs a query file against the seeded container and maps every row.
     *
     * <p>Each Task 1 query file leads with {@code SET search_path ...;} followed
     * by the SELECT, so {@link Statement#executeQuery} (which expects a single
     * ResultSet) is unusable. We {@link Statement#execute} the whole script and
     * walk results until we reach the SELECT's ResultSet. Every Connection,
     * Statement, and ResultSet is opened in try-with-resources.
     */
    private <T> List<T> runQuery(String queryFile, RowMapper<T> mapper) throws Exception {
        var sql = Files.readString(Path.of(queryFile));
        var rows = new ArrayList<T>();
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {

            boolean isResultSet = stmt.execute(sql);
            while (true) {
                if (isResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        while (rs.next()) {
                            rows.add(mapper.map(rs));
                        }
                    }
                }
                isResultSet = stmt.getMoreResults();
                if (!isResultSet && stmt.getUpdateCount() == -1) {
                    break; // no further ResultSet and no further update count
                }
            }
        }
        return rows;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private record TenantTotal(String tenantId, String displayName, BigDecimal total) {}

    private record WindowRow(String tenantId, String jurisdictionCode, BigDecimal amount,
                             int amountRank, BigDecimal tenantTotal) {}

    private record GroupRow(String tenantId, long allocationCount, BigDecimal avgIncome) {}
}
