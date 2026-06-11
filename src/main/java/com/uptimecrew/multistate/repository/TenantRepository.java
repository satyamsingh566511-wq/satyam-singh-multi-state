package com.uptimecrew.multistate.repository;

import com.uptimecrew.multistate.entity.Tenant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Primary repository (Task 2 reference pattern). PK type is String (Tenant.id). */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    /* (1) Derived query — Spring Data generates the JPQL from the method name. */
    List<Tenant> findByStatus(String status);

    /*
     * (2) Explicit @Query JPQL — an aggregate over the allocations relationship
     *     with a HAVING clause, which the derived-name convention can't express.
     */
    @Query("SELECT t FROM Tenant t JOIN t.allocations a "
            + "GROUP BY t HAVING COUNT(a) >= :minLines")
    List<Tenant> findWithAtLeastNAllocations(@Param("minLines") long minLines);
}
