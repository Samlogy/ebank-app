package com.ebank.accounts.command;

import java.math.BigDecimal;

public record UpdateAccountCommand(
        Long id,
        String accountNumber,
        String accountHolderName,
        String email,
        String phoneNumber,
        String accountType,
        BigDecimal balance,
        String address,
        String status
) {}
