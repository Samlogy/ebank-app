package com.ebank.transaction.controller;

import com.ebank.common.dto.ApiResponse;
import com.ebank.transaction.dto.TransactionResponse;
import com.ebank.transaction.dto.TransferRequest;
import com.ebank.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionService transactionService;
    
    @PostMapping("/accounts/{fromAccountId}/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @PathVariable Long fromAccountId,
            @RequestBody TransferRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TransactionResponse response = transactionService.transfer(fromAccountId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Transfer completed successfully"));
    }
    
    @GetMapping("/accounts/{accountId}/history")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @PathVariable Long accountId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<TransactionResponse> transactions = transactionService.getTransactionHistory(accountId, userId);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Transaction history retrieved"));
    }
}
