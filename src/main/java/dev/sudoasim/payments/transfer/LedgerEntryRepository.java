package dev.sudoasim.payments.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    long countByTransferId(UUID transferId);
}

