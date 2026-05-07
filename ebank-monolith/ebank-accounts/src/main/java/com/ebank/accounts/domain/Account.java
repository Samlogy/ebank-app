package com.ebank.accounts.domain;

import com.ebank.core.domain.BaseEntity;
import com.ebank.core.exception.BusinessRuleViolationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Builder
    public Account(String accountNumber, String accountHolderName, String email,
                   String phoneNumber, AccountType accountType, BigDecimal balance,
                   String address, AccountStatus status) {
        if (accountNumber == null || accountNumber.isBlank()) throw new IllegalArgumentException("Account number required");
        if (accountHolderName == null || accountHolderName.isBlank()) throw new IllegalArgumentException("Holder name required");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email required");
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.accountType = accountType != null ? accountType : AccountType.SAVINGS;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.address = address;
        this.status = status != null ? status : AccountStatus.ACTIVE;
    }

    public void deposit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Deposit amount must be positive");
        }
        requireActive();
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Withdrawal amount must be positive");
        }
        requireActive();
        if (this.balance.compareTo(amount) < 0) {
            throw new BusinessRuleViolationException("Insufficient funds: balance " + balance + " < " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void update(String accountHolderName, String email, String phoneNumber,
                       AccountType accountType, BigDecimal balance, String address, AccountStatus status) {
        this.accountHolderName = accountHolderName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.address = address;
        this.status = status;
    }

    public void close() {
        if (this.status == AccountStatus.CLOSED) {
            throw new BusinessRuleViolationException("Account is already closed");
        }
        if (this.balance.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleViolationException("Cannot close account with non-zero balance");
        }
        this.status = AccountStatus.CLOSED;
    }

    private void requireActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Account is not active: " + this.status);
        }
    }
}
