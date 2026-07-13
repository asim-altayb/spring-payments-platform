package dev.sudoasim.payments.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) { this.accounts = accounts; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_accounts:write')")
    AccountResponse create(@Valid @RequestBody CreateAccount request) {
        Account account = accounts.save(new Account(UUID.randomUUID(), request.externalReference(),
                request.currency(), request.openingBalance()));
        return AccountResponse.from(account);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_accounts:read')")
    AccountResponse find(@PathVariable UUID id) {
        return accounts.findById(id).map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public record CreateAccount(@NotBlank String externalReference,
                                @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                                @NotNull @DecimalMin("0.0000") BigDecimal openingBalance) {}
    public record AccountResponse(UUID id, String currency, BigDecimal balance) {
        static AccountResponse from(Account account) {
            return new AccountResponse(account.getId(), account.getCurrency(), account.getBalance());
        }
    }
}

