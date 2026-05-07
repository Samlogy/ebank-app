package com.ebank.transactions.api.dto;

import com.ebank.transactions.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Transaction creation request")
public record TransactionRequest(
        Long fromAccountId,
        Long toAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull TransactionType type,
        String description
) {}
