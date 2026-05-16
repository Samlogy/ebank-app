package com.ebank.transactions.api;

import com.ebank.transactions.dto.TransactionRequest;
import com.ebank.transactions.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management APIs")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "Get all transactions")
    public Flux<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public Mono<TransactionResponse> getTransactionById(@PathVariable String id) {
        return transactionService.getTransactionById(id);
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get transactions by account ID")
    public Flux<TransactionResponse> getTransactionsByAccount(@PathVariable String accountId) {
        return transactionService.getTransactionsByAccount(accountId);
    }

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public Mono<ResponseEntity<TransactionResponse>> createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.createTransaction(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }
}
