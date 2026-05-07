package com.ebank.transactions.application.command;

import com.ebank.transactions.domain.TransactionType;

import java.math.BigDecimal;

public record CreateTransactionCommand(
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransactionType type,
        String description
) {}
