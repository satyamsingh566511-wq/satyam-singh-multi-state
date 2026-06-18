package com.uptimecrew.multistate.repository;

import com.uptimecrew.multistate.entity.Allocation;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Child-entity repository. PK type is String (Allocation.id). */
@Repository
public interface AllocationRepository extends JpaRepository<Allocation, String> {

    /* (1) Derived query — Spring Data generates the JPQL from the method name. */
    List<Allocation> findByJurisdictionCode(String jurisdictionCode);

    /*
     * (3) Batch fetch for the GraphQL tenant.lines @BatchMapping. One
     *     `WHERE tenant_id IN (?, ?, ...)` SELECT loads the line items for an
     *     entire generation of parent tenants, so resolving lines across N
     *     tenants is a single round-trip rather than N. This is the query the
     *     N+1 fix hinges on — see AllocationService.loadLineItemsByParent.
     */
    List<Allocation> findByTenant_IdIn(Collection<String> tenantIds);

    /*
     * (2) Explicit @Query JPQL — a SUM aggregate grouped by jurisdiction with a
     *     HAVING threshold, which the derived-name convention can't express.
     */
    @Query("SELECT a.jurisdictionCode FROM Allocation a "
            + "GROUP BY a.jurisdictionCode HAVING SUM(a.amount) >= :minTotal")
    List<String> findJurisdictionsWithTotalAtLeast(@Param("minTotal") BigDecimal minTotal);
}
