package dev.sudoasim.payments.transfer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers", uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "idempotency_key"}))
public class Transfer {
    @Id private UUID id;
    @Column(name = "client_id", nullable = false) private String clientId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(nullable = false) private UUID sourceAccountId;
    @Column(nullable = false) private UUID destinationAccountId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransferStatus status;
    @Column(nullable = false) private Instant createdAt;

    protected Transfer() {}

    public Transfer(UUID id, String clientId, String idempotencyKey, UUID sourceAccountId,
                    UUID destinationAccountId, BigDecimal amount, String currency) {
        this.id = id;
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferStatus.COMPLETED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TransferStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
}

