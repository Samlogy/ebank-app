package com.ebank.accounts.api.dto;

import com.ebank.accounts.domain.AccountStatus;
import com.ebank.accounts.domain.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        String accountHolderName,
        String email,
        String phoneNumber,
        AccountType accountType,
        BigDecimal balance,
        String address,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
