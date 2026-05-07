package com.ebank.accounts.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private String accountHolderName;
    private String email;
    private String phoneNumber;
    private String accountType;
    private BigDecimal balance;
    private String address;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}