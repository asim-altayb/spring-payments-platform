package dev.sudoasim.payments.transfer;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(String clientId, String idempotencyKey, UUID sourceAccountId,
                              UUID destinationAccountId, BigDecimal amount, String currency) {}

