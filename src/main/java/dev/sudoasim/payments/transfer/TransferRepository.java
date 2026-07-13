package dev.sudoasim.payments.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);
}

