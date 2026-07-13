package dev.sudoasim.payments;

import dev.sudoasim.payments.account.Account;
import dev.sudoasim.payments.account.AccountRepository;
import dev.sudoasim.payments.common.DomainException;
import dev.sudoasim.payments.outbox.OutboxEventRepository;
import dev.sudoasim.payments.transfer.EntryDirection;
import dev.sudoasim.payments.transfer.LedgerEntry;
import dev.sudoasim.payments.transfer.LedgerEntryRepository;
import dev.sudoasim.payments.transfer.Transfer;
import dev.sudoasim.payments.transfer.TransferCommand;
import dev.sudoasim.payments.transfer.TransferRepository;
import dev.sudoasim.payments.transfer.TransferService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "payments.outbox-poll-ms=3600000")
@Testcontainers(disabledWithoutDocker = true)
class TransferIntegrationTest {

    // Testcontainers 2.x: PostgreSQLContainer is a concrete non-generic type.
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TransferService service;
    @Autowired AccountRepository accounts;
    @Autowired TransferRepository transfers;
    @Autowired LedgerEntryRepository ledger;
    @Autowired OutboxEventRepository outbox;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    UUID source;
    UUID destination;

    @BeforeEach
    void setUp() {
        // TRUNCATE bypasses the immutable-ledger row triggers used to block UPDATE/DELETE.
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery(
                        "TRUNCATE TABLE outbox_events, ledger_entries, transfers, accounts CASCADE")
                        .executeUpdate());

        source = UUID.randomUUID();
        destination = UUID.randomUUID();
        accounts.save(new Account(source, "source-" + source, "SDG", new BigDecimal("500.0000")));
        accounts.save(new Account(destination, "destination-" + destination, "SDG", BigDecimal.ZERO));
    }

    @Test
    void transferIsBalancedAuditableAndIdempotent() {
        var command = new TransferCommand("mobile", "pay-2026-00001", source, destination,
                new BigDecimal("125.5000"), "SDG");

        Transfer first = service.execute(command);
        Transfer replay = service.execute(command);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(transfers.count()).isEqualTo(1);
        assertThat(ledger.countByTransferId(first.getId())).isEqualTo(2);
        assertThat(ledger.countByTransferIdAndDirection(first.getId(), EntryDirection.DEBIT)).isEqualTo(1);
        assertThat(ledger.countByTransferIdAndDirection(first.getId(), EntryDirection.CREDIT)).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(accounts.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("374.5000");
        assertThat(accounts.findById(destination).orElseThrow().getBalance()).isEqualByComparingTo("125.5000");
    }

    @Test
    void replayingIdempotencyKeyDoesNotMoveMoneyTwice() {
        var command = new TransferCommand("mobile", "idem-replay-1", source, destination,
                new BigDecimal("50.0000"), "SDG");

        service.execute(command);
        service.execute(command);
        service.execute(command);

        assertThat(transfers.count()).isEqualTo(1);
        assertThat(ledger.count()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(accounts.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("450.0000");
        assertThat(accounts.findById(destination).orElseThrow().getBalance()).isEqualByComparingTo("50.0000");
    }

    @Test
    void insufficientFundsRollsBackEverything() {
        var command = new TransferCommand("mobile", "overdraft-1", source, destination,
                new BigDecimal("500.0001"), "SDG");

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(DomainException.class)
                .hasMessage("Insufficient funds");

        assertThat(transfers.count()).isZero();
        assertThat(ledger.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(accounts.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("500.0000");
        assertThat(accounts.findById(destination).orElseThrow().getBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    void failedTransactionCreatesNoLedgerOrOutboxRecords() {
        UUID missing = UUID.randomUUID();
        var command = new TransferCommand("mobile", "missing-dest", source, missing,
                new BigDecimal("10.0000"), "SDG");

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(DomainException.class)
                .hasMessage("Destination account not found");

        assertThat(transfers.count()).isZero();
        assertThat(ledger.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(accounts.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("500.0000");
    }

    @Test
    void completedTransferHasExactlyOneDebitAndOneCredit() {
        Transfer transfer = service.execute(new TransferCommand("mobile", "balanced-1", source, destination,
                new BigDecimal("40.2500"), "SDG"));

        List<LedgerEntry> entries = ledger.findByTransferIdOrderByDirectionAsc(transfer.getId());
        assertThat(entries).hasSize(2);

        LedgerEntry credit = entries.stream().filter(e -> e.getDirection() == EntryDirection.CREDIT).findFirst().orElseThrow();
        LedgerEntry debit = entries.stream().filter(e -> e.getDirection() == EntryDirection.DEBIT).findFirst().orElseThrow();

        assertThat(debit.getAccountId()).isEqualTo(source);
        assertThat(credit.getAccountId()).isEqualTo(destination);
        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
        assertThat(debit.getCurrency()).isEqualTo(credit.getCurrency());
        assertThat(debit.getAmount()).isEqualByComparingTo("40.2500");
    }

    @Test
    void concurrentTransfersCannotSilentlyCorruptBalances() throws Exception {
        int workers = 8;
        BigDecimal amount = new BigDecimal("10.0000");
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger retryable = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            final int index = i;
            tasks.add(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    // Unique keys: each successful transfer must debit exactly once.
                    service.execute(new TransferCommand("mobile", "concurrent-" + index, source, destination,
                            amount, "SDG"));
                    successes.incrementAndGet();
                } catch (DomainException ex) {
                    if (ex.getMessage().contains("retry safely")) {
                        retryable.incrementAndGet();
                    } else {
                        throw ex;
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // Retry losers once so every intended transfer eventually settles without overdraft.
        for (int i = 0; i < workers; i++) {
            try {
                service.execute(new TransferCommand("mobile", "concurrent-" + i, source, destination,
                        amount, "SDG"));
            } catch (DomainException ex) {
                if (!ex.getMessage().contains("retry safely") && !ex.getMessage().contains("Insufficient funds")) {
                    throw ex;
                }
            }
        }

        long transferCount = transfers.count();
        BigDecimal expectedDebited = amount.multiply(BigDecimal.valueOf(transferCount));
        BigDecimal sourceBalance = accounts.findById(source).orElseThrow().getBalance();
        BigDecimal destinationBalance = accounts.findById(destination).orElseThrow().getBalance();

        assertThat(sourceBalance.add(destinationBalance)).isEqualByComparingTo("500.0000");
        assertThat(sourceBalance).isEqualByComparingTo(new BigDecimal("500.0000").subtract(expectedDebited));
        assertThat(destinationBalance).isEqualByComparingTo(expectedDebited);
        assertThat(ledger.count()).isEqualTo(transferCount * 2);
        assertThat(outbox.count()).isEqualTo(transferCount);
        assertThat(sourceBalance.signum()).isGreaterThanOrEqualTo(0);
        assertThat(successes.get() + retryable.get()).isEqualTo(workers);
    }

    @Test
    void flywayMigrationsAppliedAgainstRealPostgreSQL() {
        Number tables = (Number) entityManager.createNativeQuery("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('accounts', 'transfers', 'ledger_entries', 'outbox_events')
                """).getSingleResult();
        assertThat(tables.intValue()).isEqualTo(4);

        Number immutableTrigger = (Number) entityManager.createNativeQuery("""
                select count(*) from pg_trigger
                where tgname = 'immutable_ledger_update'
                """).getSingleResult();
        assertThat(immutableTrigger.intValue()).isEqualTo(1);
    }

    @Test
    void ledgerEntriesAreImmutableAtDatabaseBoundary() {
        Transfer transfer = service.execute(new TransferCommand("mobile", "immutable-1", source, destination,
                new BigDecimal("5.0000"), "SDG"));
        LedgerEntry entry = ledger.findByTransferIdOrderByDirectionAsc(transfer.getId()).get(0);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery("update ledger_entries set amount = amount + 1 where id = :id")
                        .setParameter("id", entry.getId())
                        .executeUpdate()))
                .hasStackTraceContaining("ledger entries are immutable");
    }
}
