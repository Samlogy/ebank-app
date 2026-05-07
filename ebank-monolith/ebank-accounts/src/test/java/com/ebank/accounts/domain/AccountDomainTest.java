package com.ebank.accounts.domain;

import com.ebank.core.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class AccountDomainTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .accountNumber("ACC-001")
                .accountHolderName("Alice")
                .email("alice@example.com")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void deposit_increasesBalance() {
        account.deposit(new BigDecimal("500.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void withdraw_decreasesBalance() {
        account.withdraw(new BigDecimal("200.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void withdraw_throwsWhenInsufficientFunds() {
        assertThatThrownBy(() -> account.withdraw(new BigDecimal("2000.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void deposit_throwsWhenAccountInactive() {
        account.update("Alice", "alice@example.com", null, AccountType.SAVINGS,
                BigDecimal.ZERO, null, AccountStatus.INACTIVE);
        assertThatThrownBy(() -> account.deposit(new BigDecimal("100.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void close_throwsWhenBalanceNotZero() {
        assertThatThrownBy(() -> account.close())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("non-zero balance");
    }

    @Test
    void close_succeedsWhenBalanceZero() {
        account.withdraw(new BigDecimal("1000.00"));
        account.close();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }
}
