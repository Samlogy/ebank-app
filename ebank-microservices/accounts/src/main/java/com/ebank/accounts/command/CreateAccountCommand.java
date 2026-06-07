package com.ebank.accounts.command;

import java.math.BigDecimal;

public record CreateAccountCommand(
        String accountNumber,
        String accountHolderName,
        String email,
        String phoneNumber,
        String accountType,
        BigDecimal balance,
        String address,
        String status
) {}
