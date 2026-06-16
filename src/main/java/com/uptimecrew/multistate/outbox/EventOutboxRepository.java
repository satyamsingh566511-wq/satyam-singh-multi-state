package com.uptimecrew.multistate.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, java.util.UUID> {

    @Query(value = """
            SELECT e FROM EventOutboxEntity e
            WHERE e.publishedAt IS NULL
            ORDER BY e.occurredAt ASC
            """)
    List<EventOutboxEntity> findUnpublishedForUpdate(Pageable pageable);
}
