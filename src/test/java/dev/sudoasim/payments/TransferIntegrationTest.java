package dev.sudoasim.payments;

import dev.sudoasim.payments.account.Account;
import dev.sudoasim.payments.account.AccountRepository;
import dev.sudoasim.payments.outbox.OutboxEventRepository;
import dev.sudoasim.payments.transfer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "payments.outbox-poll-ms=3600000")
@Testcontainers(disabledWithoutDocker = true)
class TransferIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

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

    UUID source;
    UUID destination;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        ledger.deleteAll();
        transfers.deleteAll();
        accounts.deleteAll();
        source = UUID.randomUUID();
        destination = UUID.randomUUID();
        accounts.save(new Account(source, "source", "SDG", new BigDecimal("500.0000")));
        accounts.save(new Account(destination, "destination", "SDG", BigDecimal.ZERO));
    }

    @Test
    void transferIsBalancedAuditableAndIdempotent() {
        var command = new TransferCommand("mobile", "pay-2026-00001", source, destination,
                new BigDecimal("125.5000"), "SDG");

        Transfer first = service.execute(command);
        Transfer replay = service.execute(command);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(ledger.countByTransferId(first.getId())).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(accounts.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("374.5000");
        assertThat(accounts.findById(destination).orElseThrow().getBalance()).isEqualByComparingTo("125.5000");
    }
}
