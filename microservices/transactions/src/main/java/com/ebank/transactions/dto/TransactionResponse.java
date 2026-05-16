package com.ebank.transactions.dto;

import com.ebank.transactions.api.TransactionStatus;
import com.ebank.transactions.api.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private String id;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String referenceNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
