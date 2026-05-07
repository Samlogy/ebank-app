package com.ebank.accounts.application.command;

import com.ebank.accounts.domain.AccountStatus;
import com.ebank.accounts.domain.AccountType;

import java.math.BigDecimal;

public record UpdateAccountCommand(
        Long id,
        String accountHolderName,
        String email,
        String phoneNumber,
        AccountType accountType,
        BigDecimal balance,
        String address,
        AccountStatus status
) {}
