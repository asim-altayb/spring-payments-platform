package dev.sudoasim.payments.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    long countByTransferId(UUID transferId);

    List<LedgerEntry> findByTransferIdOrderByDirectionAsc(UUID transferId);

    @Query("select count(e) from LedgerEntry e where e.transferId = :transferId and e.direction = :direction")
    long countByTransferIdAndDirection(UUID transferId, EntryDirection direction);
}
