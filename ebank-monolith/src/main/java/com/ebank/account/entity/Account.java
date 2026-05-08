package com.ebank.account.entity;

import com.ebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_accounts_user_id", columnList = "user_id"),
    @Index(name = "idx_accounts_account_number", columnList = "account_number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;
    
    public enum AccountType {
        CHECKING, SAVINGS, INVESTMENT
    }
    
    public enum AccountStatus {
        ACTIVE, FROZEN, CLOSED
    }
}
