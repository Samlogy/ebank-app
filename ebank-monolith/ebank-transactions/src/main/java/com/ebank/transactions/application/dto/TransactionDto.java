package com.ebank.transactions.application.dto;

import com.ebank.transactions.domain.TransactionStatus;
import com.ebank.transactions.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDto(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        String referenceNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
