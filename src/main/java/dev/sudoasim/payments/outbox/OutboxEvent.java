package dev.sudoasim.payments.outbox;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(nullable = false) private String aggregateType;
    @Column(nullable = false) private UUID aggregateId;
    @Column(nullable = false) private String eventType;
    @Column(nullable = false, columnDefinition = "jsonb") private String payload;
    @Column(nullable = false) private Instant occurredAt;
    private Instant publishedAt;
    @Column(nullable = false) private int attempts;

    protected OutboxEvent() {}

    public static OutboxEvent transferCompleted(UUID transferId, BigDecimal amount, String currency) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = "transfer";
        event.aggregateId = transferId;
        event.eventType = "transfer.completed";
        event.payload = "{\"transferId\":\"" + transferId + "\",\"amount\":\"" + amount + "\",\"currency\":\"" + currency + "\"}";
        event.occurredAt = Instant.now();
        return event;
    }

    public void markPublished() { this.publishedAt = Instant.now(); }
    public void recordAttempt() { this.attempts++; }
    public UUID getId() { return id; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
}

