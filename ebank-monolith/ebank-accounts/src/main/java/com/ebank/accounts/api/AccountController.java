package com.ebank.accounts.api;

import com.ebank.accounts.api.dto.AccountRequest;
import com.ebank.accounts.api.dto.AccountResponse;
import com.ebank.accounts.application.command.CreateAccountCommand;
import com.ebank.accounts.application.command.UpdateAccountCommand;
import com.ebank.accounts.application.dto.AccountDto;
import com.ebank.accounts.application.port.in.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Accounts", description = "Bank account management")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final UpdateAccountUseCase updateAccountUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final GetAccountUseCase getAccountUseCase;

    @GetMapping
    @Operation(summary = "Get all accounts")
    public List<AccountResponse> getAll() {
        return getAccountUseCase.getAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public AccountResponse getById(@PathVariable Long id) {
        return toResponse(getAccountUseCase.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new account")
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        AccountDto dto = createAccountUseCase.create(new CreateAccountCommand(
                request.accountNumber(), request.accountHolderName(), request.email(),
                request.phoneNumber(), request.accountType(), request.balance(),
                request.address(), request.status()));
        return toResponse(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        AccountDto dto = updateAccountUseCase.update(new UpdateAccountCommand(
                id, request.accountHolderName(), request.email(), request.phoneNumber(),
                request.accountType(), request.balance(), request.address(), request.status()));
        return toResponse(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an account")
    public void delete(@PathVariable Long id) {
        deleteAccountUseCase.delete(id);
    }

    private AccountResponse toResponse(AccountDto dto) {
        return new AccountResponse(dto.id(), dto.accountNumber(), dto.accountHolderName(),
                dto.email(), dto.phoneNumber(), dto.accountType(), dto.balance(),
                dto.address(), dto.status(), dto.createdAt(), dto.updatedAt());
    }
}
