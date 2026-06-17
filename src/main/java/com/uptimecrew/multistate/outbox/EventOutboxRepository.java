package com.uptimecrew.multistate.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, java.util.UUID> {

    @Query(value = """
            SELECT * FROM multistate.event_outbox
            WHERE published_at IS NULL
            ORDER BY occurred_at ASC
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EventOutboxEntity> findUnpublishedForUpdate(Pageable pageable);
}
