package com.ebank.transactions.api;

import com.ebank.transactions.api.dto.TransactionRequest;
import com.ebank.transactions.api.dto.TransactionResponse;
import com.ebank.transactions.application.command.CreateTransactionCommand;
import com.ebank.transactions.application.dto.TransactionDto;
import com.ebank.transactions.application.port.in.CreateTransactionUseCase;
import com.ebank.transactions.application.port.in.GetTransactionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Financial transaction management")
public class TransactionController {

    private final CreateTransactionUseCase createTransactionUseCase;
    private final GetTransactionUseCase getTransactionUseCase;

    @GetMapping
    @Operation(summary = "Get all transactions")
    public List<TransactionResponse> getAll() {
        return getTransactionUseCase.getAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public TransactionResponse getById(@PathVariable Long id) {
        return toResponse(getTransactionUseCase.getById(id));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get all transactions for an account")
    public List<TransactionResponse> getByAccount(@PathVariable Long accountId) {
        return getTransactionUseCase.getByAccountId(accountId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new transaction (debit + credit + persist atomically)")
    public TransactionResponse create(@Valid @RequestBody TransactionRequest request) {
        TransactionDto dto = createTransactionUseCase.create(new CreateTransactionCommand(
                request.fromAccountId(), request.toAccountId(),
                request.amount(), request.type(), request.description()));
        return toResponse(dto);
    }

    private TransactionResponse toResponse(TransactionDto dto) {
        return new TransactionResponse(dto.id(), dto.fromAccountId(), dto.toAccountId(),
                dto.amount(), dto.type(), dto.status(), dto.description(),
                dto.referenceNumber(), dto.createdAt(), dto.updatedAt());
    }
}
