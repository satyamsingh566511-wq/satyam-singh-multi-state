package com.uptimecrew.multistate.repository;

import com.uptimecrew.multistate.entity.Jurisdiction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

// Reference-table repository. PK type is String (Jurisdiction.code).
@Repository
public interface JurisdictionRepository extends JpaRepository<Jurisdiction, String> {

    // (1) Derived query — Spring Data generates the JPQL from the method name.
    List<Jurisdiction> findByKind(String kind);

    // (2) Explicit @Query JPQL — a correlated subquery (rate above the table
    //     average) that the derived-name convention can't express.
    @Query("SELECT j FROM Jurisdiction j WHERE j.topMarginalRate > "
            + "(SELECT AVG(j2.topMarginalRate) FROM Jurisdiction j2)")
    List<Jurisdiction> findWithAboveAverageRate();
}
