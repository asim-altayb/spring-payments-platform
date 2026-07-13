package dev.sudoasim.payments.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final TransferService service;

    public TransferController(TransferService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_transfers:write')")
    TransferResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                            @RequestHeader(value = "X-Client-Id", defaultValue = "public-api") String clientId,
                            @Valid @RequestBody TransferRequest request) {
        Transfer transfer = service.execute(new TransferCommand(clientId, idempotencyKey,
                request.sourceAccountId(), request.destinationAccountId(), request.amount(), request.currency()));
        return new TransferResponse(transfer.getId(), transfer.getStatus(), transfer.getAmount(), transfer.getCurrency());
    }

    public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
                                  @NotNull @DecimalMin("0.0001") BigDecimal amount,
                                  @NotBlank String currency) {}

    public record TransferResponse(UUID id, TransferStatus status, BigDecimal amount, String currency) {}
}

