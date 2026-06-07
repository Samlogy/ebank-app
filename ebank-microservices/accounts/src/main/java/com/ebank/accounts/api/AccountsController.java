package com.ebank.accounts.api;

import com.ebank.accounts.dto.AccountRequest;
import com.ebank.accounts.dto.AccountResponse;
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
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountsController {

    private final AccountService accountService;

    /**
     * GET all accounts
     */
    @GetMapping
    public Flux<AccountResponse> getAllAccounts() {
        log.info("Fetching all accounts");
        return accountService.getAllAccounts();
    }

    /**
     * GET account by ID
     */
    @GetMapping("/{id}")
    public Mono<AccountResponse> getAccountById(@PathVariable Long id) {
        log.info("Fetching account with id: {}", id);
        return accountService.getAccountById(id);
    }

    /**
     * POST create new account
     */
    @PostMapping
    public Mono<ResponseEntity<AccountResponse>> createAccount(@Valid @RequestBody AccountRequest accountRequest) {
        log.info("Creating new account with accountNumber: {}, email: {}",
                accountRequest.getAccountNumber(), accountRequest.getEmail());
        return accountService.createAccount(accountRequest)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    /**
     * PUT update existing account
     */
    @PutMapping("/{id}")
    public Mono<AccountResponse> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountRequest accountRequest) {
        log.info("Updating account with id: {}", id);
        return accountService.updateAccount(id, accountRequest);
    }

    /**
     * DELETE account
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAccount(@PathVariable Long id) {
        log.info("Deleting account with id: {}", id);
        return accountService.deleteAccount(id)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
