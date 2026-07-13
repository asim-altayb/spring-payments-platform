package dev.sudoasim.payments.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = "select * from outbox_events where published_at is null and attempts < 8 " +
            "order by occurred_at for update skip locked limit 50", nativeQuery = true)
    List<OutboxEvent> lockNextBatch();
}
