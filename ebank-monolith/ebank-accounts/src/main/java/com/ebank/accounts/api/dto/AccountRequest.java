package com.ebank.accounts.api.dto;

import com.ebank.accounts.domain.AccountStatus;
import com.ebank.accounts.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Account create/update request")
public record AccountRequest(
        @NotBlank @Size(min = 5, max = 20)
        @Schema(description = "Unique account number") String accountNumber,

        @NotBlank @Size(max = 100) String accountHolderName,

        @NotBlank @Email @Size(max = 100) String email,

        @Size(max = 20) String phoneNumber,

        @NotNull AccountType accountType,

        @NotNull @DecimalMin("0.00") BigDecimal balance,

        @Size(max = 200) String address,

        AccountStatus status
) {}
