package dev.sudoasim.payments.account;

import dev.sudoasim.payments.common.DomainException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String externalReference;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal balance;
    @Version private long version;

    protected Account() {}

    public Account(UUID id, String externalReference, String currency, BigDecimal openingBalance) {
        if (openingBalance.signum() < 0) throw new DomainException("Opening balance cannot be negative");
        this.id = id;
        this.externalReference = externalReference;
        this.currency = currency;
        this.balance = openingBalance;
    }

    public void debit(BigDecimal amount) {
        if (amount.signum() <= 0) throw new DomainException("Amount must be positive");
        if (balance.compareTo(amount) < 0) throw new DomainException("Insufficient funds");
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount.signum() <= 0) throw new DomainException("Amount must be positive");
        balance = balance.add(amount);
    }

    public UUID getId() { return id; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
}

