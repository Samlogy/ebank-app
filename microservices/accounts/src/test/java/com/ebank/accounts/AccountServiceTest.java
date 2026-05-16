package com.ebank.accounts;

import com.ebank.accounts.api.Account;
import com.ebank.accounts.api.AccountRepository;
import com.ebank.accounts.api.AccountService;
import com.ebank.accounts.cache.AccountCacheService;
import com.ebank.accounts.command.handler.AccountCommandHandler;
import com.ebank.accounts.dto.AccountRequest;
import com.ebank.accounts.dto.AccountResponse;
import com.ebank.accounts.exception.BusinessException;
import com.ebank.accounts.exception.ResourceNotFoundException;
import com.ebank.accounts.query.handler.AccountQueryHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountCacheService cacheService;

    private AccountCommandHandler commandHandler;
    private AccountQueryHandler queryHandler;
    private AccountService accountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Cache always misses so queries hit the repository — all lenient (not all stubs needed by every test)
        when(cacheService.getCachedById(anyLong())).thenReturn(Mono.empty());
        when(cacheService.getCachedAll()).thenReturn(Mono.empty());
        when(cacheService.putById(any())).thenReturn(Mono.just(false));
        when(cacheService.putAll(any())).thenReturn(Mono.just(false));
        when(cacheService.evictById(anyLong())).thenReturn(Mono.just(false));
        when(cacheService.evictAll()).thenReturn(Mono.just(false));
        when(cacheService.evictByIdAndAll(anyLong())).thenReturn(Mono.just(false));

        commandHandler = new AccountCommandHandler(accountRepository, cacheService, new SimpleMeterRegistry());
        queryHandler = new AccountQueryHandler(accountRepository, cacheService);
        accountService = new AccountService(commandHandler, queryHandler);

        testAccount = new Account();
        testAccount.setId(1L);
        testAccount.setAccountNumber("ACC123456789");
        testAccount.setAccountHolderName("John Doe");
        testAccount.setEmail("john.doe@example.com");
        testAccount.setPhoneNumber("1234567890");
        testAccount.setAccountType("SAVINGS");
        testAccount.setBalance(BigDecimal.valueOf(100.00));
        testAccount.setAddress("123 Main St, City, State 12345");
        testAccount.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("getAccountById should return AccountResponse when account exists")
    void getAccountById_ShouldReturnResponse_WhenAccountExists() {
        when(accountRepository.findById(1L)).thenReturn(Mono.just(testAccount));

        StepVerifier.create(accountService.getAccountById(1L))
                .expectNextMatches(response ->
                        response.getId().equals(1L) &&
                        response.getEmail().equals("john.doe@example.com") &&
                        response.getAccountNumber().equals("ACC123456789") &&
                        response.getAccountHolderName().equals("John Doe"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getAccountById should emit ResourceNotFoundException when account not found")
    void getAccountById_ShouldEmitError_WhenAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(accountService.getAccountById(999L))
                .expectErrorMatches(ex ->
                        ex instanceof ResourceNotFoundException &&
                        ex.getMessage().contains("Account") &&
                        ex.getMessage().contains("999"))
                .verify();
    }

    @Test
    @DisplayName("createAccount should save and return AccountResponse on success")
    void createAccount_ShouldReturnResponse_WhenSuccessful() {
        AccountRequest request = buildRequest("ACC123456789", "john.doe@example.com");

        when(accountRepository.existsByAccountNumber("ACC123456789")).thenReturn(Mono.just(false));
        when(accountRepository.existsByEmail("john.doe@example.com")).thenReturn(Mono.just(false));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));

        StepVerifier.create(accountService.createAccount(request))
                .expectNextMatches(response ->
                        response.getAccountNumber().equals("ACC123456789") &&
                        response.getEmail().equals("john.doe@example.com"))
                .verifyComplete();
    }

    @Test
    @DisplayName("createAccount should emit BusinessException when accountNumber already exists")
    void createAccount_ShouldEmitError_WhenDuplicateAccountNumber() {
        AccountRequest request = buildRequest("ACC123456789", "other@example.com");

        when(accountRepository.existsByAccountNumber("ACC123456789")).thenReturn(Mono.just(true));

        StepVerifier.create(accountService.createAccount(request))
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("Account number already exists"))
                .verify();
    }

    @Test
    @DisplayName("createAccount should emit BusinessException when email already exists")
    void createAccount_ShouldEmitError_WhenDuplicateEmail() {
        AccountRequest request = buildRequest("NEWACC12345", "john.doe@example.com");

        when(accountRepository.existsByAccountNumber("NEWACC12345")).thenReturn(Mono.just(false));
        when(accountRepository.existsByEmail("john.doe@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(accountService.createAccount(request))
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("Email already exists"))
                .verify();
    }

    @Nested
    @DisplayName("updateAccount scenarios")
    class UpdateAccountScenarios {

        @Test
        @DisplayName("updateAccount should emit ResourceNotFoundException when account not found")
        void updateAccount_ShouldEmitError_WhenAccountNotFound() {
            AccountRequest request = buildRequest("ACC123456789", "john.doe@example.com");

            when(accountRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(accountService.updateAccount(999L, request))
                    .expectErrorMatches(ex ->
                            ex instanceof ResourceNotFoundException &&
                            ex.getMessage().contains("999"))
                    .verify();
        }

        @Test
        @DisplayName("updateAccount should emit BusinessException when new email already taken")
        void updateAccount_ShouldEmitError_WhenEmailTaken() {
            AccountRequest request = buildRequest("ACC123456789", "taken@example.com");

            when(accountRepository.findById(1L)).thenReturn(Mono.just(testAccount));
            when(accountRepository.existsByEmailAndIdNot("taken@example.com", 1L))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(accountService.updateAccount(1L, request))
                    .expectErrorMatches(ex ->
                            ex instanceof BusinessException &&
                            ex.getMessage().contains("Email already exists"))
                    .verify();
        }

        @Test
        @DisplayName("updateAccount should succeed when email is unchanged")
        void updateAccount_ShouldSucceed_WhenEmailUnchanged() {
            AccountRequest request = buildRequest("ACC123456789", "john.doe@example.com");

            when(accountRepository.findById(1L)).thenReturn(Mono.just(testAccount));
            when(accountRepository.existsByEmailAndIdNot("john.doe@example.com", 1L))
                    .thenReturn(Mono.just(false));
            when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));

            StepVerifier.create(accountService.updateAccount(1L, request))
                    .expectNextMatches(response -> response.getId().equals(1L))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteAccount scenarios")
    class DeleteAccountScenarios {

        @Test
        @DisplayName("deleteAccount should complete when account exists")
        void deleteAccount_ShouldComplete_WhenAccountExists() {
            when(accountRepository.findById(1L)).thenReturn(Mono.just(testAccount));
            when(accountRepository.deleteById(anyLong())).thenReturn(Mono.empty());

            StepVerifier.create(accountService.deleteAccount(1L))
                    .verifyComplete();
        }

        @Test
        @DisplayName("deleteAccount should emit ResourceNotFoundException when account not found")
        void deleteAccount_ShouldEmitError_WhenAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(accountService.deleteAccount(999L))
                    .expectErrorMatches(ex ->
                            ex instanceof ResourceNotFoundException &&
                            ex.getMessage().contains("999"))
                    .verify();
        }
    }

    // ---- helpers ----

    private AccountRequest buildRequest(String accountNumber, String email) {
        AccountRequest req = new AccountRequest();
        req.setAccountNumber(accountNumber);
        req.setAccountHolderName("John Doe");
        req.setEmail(email);
        req.setPhoneNumber("1234567890");
        req.setAccountType("SAVINGS");
        req.setBalance(BigDecimal.valueOf(100.00));
        req.setAddress("123 Main St");
        req.setStatus("ACTIVE");
        return req;
    }
}
