package com.ebank.transactions.domain.event;

import com.ebank.transactions.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionCreatedEvent(
        Long transactionId,
        String referenceNumber,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String type,
        String status,
        LocalDateTime occurredAt
) {
    public static TransactionCreatedEvent from(Transaction tx) {
        return new TransactionCreatedEvent(
                tx.getId(),
                tx.getReferenceNumber(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getType().name(),
                tx.getStatus().name(),
                LocalDateTime.now()
        );
    }
}
