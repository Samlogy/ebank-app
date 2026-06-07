package com.ebank.transactions.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionEvent(
        String eventType,
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        String type,
        String status,
        LocalDateTime occurredAt
) {}
