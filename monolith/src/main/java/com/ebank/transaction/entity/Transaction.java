package com.ebank.transaction.entity;

import com.ebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_from_account", columnList = "from_account_id"),
    @Index(name = "idx_transactions_to_account", columnList = "to_account_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity {
    
    @Column(nullable = false)
    private Long fromAccountId;
    
    @Column(nullable = false)
    private Long toAccountId;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Column(unique = true)
    private String reference;
    
    private String description;
    
    public enum TransactionType {
        TRANSFER, DEPOSIT, WITHDRAWAL
    }
    
    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }
}
