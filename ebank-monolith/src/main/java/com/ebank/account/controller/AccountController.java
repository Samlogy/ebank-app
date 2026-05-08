package com.ebank.account.controller;

import com.ebank.account.dto.AccountResponse;
import com.ebank.account.dto.CreateAccountRequest;
import com.ebank.account.service.AccountService;
import com.ebank.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @RequestBody CreateAccountRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AccountResponse response = accountService.createAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Account created successfully"));
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getUserAccounts(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<AccountResponse> accounts = accountService.getUserAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success(accounts, "Accounts retrieved"));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AccountResponse account = accountService.getAccount(id, userId);
        return ResponseEntity.ok(ApiResponse.success(account, "Account retrieved"));
    }
}
