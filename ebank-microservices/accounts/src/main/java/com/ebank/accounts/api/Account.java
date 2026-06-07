package com.ebank.accounts.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    private Long id;

    private String accountNumber;

    private String accountHolderName;

    private String email;

    private String phoneNumber;

    private String accountType; // SAVINGS, CHECKING, BUSINESS

    private BigDecimal balance;

    private String address;

    private String status; // ACTIVE, INACTIVE, CLOSED

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
