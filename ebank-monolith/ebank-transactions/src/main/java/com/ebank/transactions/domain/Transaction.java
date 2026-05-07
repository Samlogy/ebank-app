package com.ebank.transactions.domain;

import com.ebank.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseEntity {

    @Column(name = "from_account_id")
    private Long fromAccountId;

    @Column(name = "to_account_id")
    private Long toAccountId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "reference_number", nullable = false, unique = true, length = 36)
    private String referenceNumber;

    public static Transaction create(Long fromAccountId, Long toAccountId, BigDecimal amount,
                                     TransactionType type, String description) {
        Transaction tx = new Transaction();
        tx.fromAccountId = fromAccountId;
        tx.toAccountId = toAccountId;
        tx.amount = amount;
        tx.type = type;
        tx.status = TransactionStatus.PENDING;
        tx.description = description;
        tx.referenceNumber = UUID.randomUUID().toString();
        return tx;
    }

    public void complete() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can be completed");
        }
        this.status = TransactionStatus.COMPLETED;
    }

    public void fail() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING transactions can fail");
        }
        this.status = TransactionStatus.FAILED;
    }
}
