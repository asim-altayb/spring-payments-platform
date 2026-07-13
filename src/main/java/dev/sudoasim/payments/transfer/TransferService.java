package dev.sudoasim.payments.transfer;

import dev.sudoasim.payments.account.Account;
import dev.sudoasim.payments.account.AccountRepository;
import dev.sudoasim.payments.common.DomainException;
import dev.sudoasim.payments.outbox.OutboxEvent;
import dev.sudoasim.payments.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final OutboxEventRepository outbox;
    private final Counter completed;

    public TransferService(AccountRepository accounts, TransferRepository transfers,
                           LedgerEntryRepository ledger, OutboxEventRepository outbox, MeterRegistry registry) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.outbox = outbox;
        this.completed = registry.counter("payments.transfers.completed");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transfer execute(TransferCommand command) {
        validate(command);
        var existing = transfers.findByClientIdAndIdempotencyKey(command.clientId(), command.idempotencyKey());
        if (existing.isPresent()) return existing.get();

        Account source = accounts.findById(command.sourceAccountId())
                .orElseThrow(() -> new DomainException("Source account not found"));
        Account destination = accounts.findById(command.destinationAccountId())
                .orElseThrow(() -> new DomainException("Destination account not found"));
        if (!source.getCurrency().equals(command.currency()) || !destination.getCurrency().equals(command.currency()))
            throw new DomainException("Account currency mismatch");

        source.debit(command.amount());
        destination.credit(command.amount());
        UUID transferId = UUID.randomUUID();
        Transfer transfer = new Transfer(transferId, command.clientId(), command.idempotencyKey(),
                source.getId(), destination.getId(), command.amount(), command.currency());
        try {
            transfers.saveAndFlush(transfer);
            ledger.save(new LedgerEntry(transferId, source.getId(), EntryDirection.DEBIT, command.amount(), command.currency()));
            ledger.save(new LedgerEntry(transferId, destination.getId(), EntryDirection.CREDIT, command.amount(), command.currency()));
            outbox.save(OutboxEvent.transferCompleted(transferId, command.amount(), command.currency()));
            completed.increment();
            return transfer;
        } catch (ObjectOptimisticLockingFailureException concurrentUpdate) {
            throw new DomainException("Concurrent balance update; retry safely with the same idempotency key");
        }
    }

    private static void validate(TransferCommand command) {
        if (command.sourceAccountId().equals(command.destinationAccountId()))
            throw new DomainException("Source and destination must differ");
        if (command.amount() == null || command.amount().signum() <= 0)
            throw new DomainException("Amount must be positive");
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank())
            throw new DomainException("Idempotency key is required");
    }
}
