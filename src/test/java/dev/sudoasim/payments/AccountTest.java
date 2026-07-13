package dev.sudoasim.payments;

import dev.sudoasim.payments.account.Account;
import dev.sudoasim.payments.common.DomainException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AccountTest {
    @Test
    void rejectsOverdraftWithoutChangingBalance() {
        Account account = new Account(UUID.randomUUID(), "wallet-1", "SDG", new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(DomainException.class).hasMessage("Insufficient funds");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void creditAndDebitPreserveDecimalPrecision() {
        Account account = new Account(UUID.randomUUID(), "wallet-2", "SDG", new BigDecimal("10.0000"));
        account.credit(new BigDecimal("0.1250"));
        account.debit(new BigDecimal("1.0250"));
        assertThat(account.getBalance()).isEqualByComparingTo("9.1000");
    }
}

