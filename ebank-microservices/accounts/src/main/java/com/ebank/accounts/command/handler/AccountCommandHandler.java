package com.ebank.accounts.command.handler;

import com.ebank.accounts.api.Account;
import com.ebank.accounts.api.AccountRepository;
import com.ebank.accounts.cache.AccountCacheService;
import com.ebank.accounts.command.CreateAccountCommand;
import com.ebank.accounts.command.DeleteAccountCommand;
import com.ebank.accounts.command.UpdateAccountCommand;
import com.ebank.accounts.exception.BusinessException;
import com.ebank.accounts.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AccountCommandHandler {

    private final AccountRepository accountRepository;
    private final AccountCacheService cacheService;

    // Business operation counters — tagged with operation for single metric query
    private final Counter createCounter;
    private final Counter updateCounter;
    private final Counter deleteCounter;
    private final Counter errorCounter;

    public AccountCommandHandler(AccountRepository accountRepository,
                                 AccountCacheService cacheService,
                                 MeterRegistry registry) {
        this.accountRepository = accountRepository;
        this.cacheService = cacheService;
        this.createCounter = Counter.builder("accounts.operations")
                .tag("operation", "create").tag("status", "success")
                .description("Number of accounts successfully created")
                .register(registry);
        this.updateCounter = Counter.builder("accounts.operations")
                .tag("operation", "update").tag("status", "success")
                .description("Number of accounts successfully updated")
                .register(registry);
        this.deleteCounter = Counter.builder("accounts.operations")
                .tag("operation", "delete").tag("status", "success")
                .description("Number of accounts successfully deleted")
                .register(registry);
        this.errorCounter = Counter.builder("accounts.operations")
                .tag("operation", "any").tag("status", "error")
                .description("Number of failed account operations")
                .register(registry);
    }

    public Mono<Account> handle(CreateAccountCommand cmd) {
        log.debug("Handling CreateAccountCommand for accountNumber: {}", cmd.accountNumber());

        return accountRepository.existsByAccountNumber(cmd.accountNumber())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new BusinessException(
                                "Account number already exists: " + cmd.accountNumber()));
                    }
                    return accountRepository.existsByEmail(cmd.email());
                })
                .flatMap(emailExists -> {
                    if (Boolean.TRUE.equals(emailExists)) {
                        return Mono.error(new BusinessException(
                                "Email already exists: " + cmd.email()));
                    }
                    Account account = new Account();
                    account.setAccountNumber(cmd.accountNumber());
                    account.setAccountHolderName(cmd.accountHolderName());
                    account.setEmail(cmd.email());
                    account.setPhoneNumber(cmd.phoneNumber());
                    account.setAccountType(cmd.accountType());
                    account.setBalance(cmd.balance());
                    account.setAddress(cmd.address());
                    account.setStatus(cmd.status());
                    return accountRepository.save(account);
                })
                .flatMap(account ->
                    cacheService.evictAll()
                            .then(Mono.just(account))
                )
                .doOnSuccess(a -> createCounter.increment())
                .doOnError(e -> errorCounter.increment());
    }

    public Mono<Account> handle(UpdateAccountCommand cmd) {
        log.debug("Handling UpdateAccountCommand for id: {}", cmd.id());

        return accountRepository.findById(cmd.id())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Account", "id", cmd.id())))
                .flatMap(existing -> accountRepository.existsByEmailAndIdNot(cmd.email(), cmd.id())
                        .flatMap(emailTaken -> {
                            if (Boolean.TRUE.equals(emailTaken)) {
                                return Mono.error(new BusinessException(
                                        "Email already exists: " + cmd.email()));
                            }
                            existing.setAccountHolderName(cmd.accountHolderName());
                            existing.setEmail(cmd.email());
                            existing.setPhoneNumber(cmd.phoneNumber());
                            existing.setAccountType(cmd.accountType());
                            existing.setBalance(cmd.balance());
                            existing.setAddress(cmd.address());
                            existing.setStatus(cmd.status());
                            return accountRepository.save(existing);
                        }))
                .flatMap(account ->
                    cacheService.evictByIdAndAll(account.getId())
                            .then(Mono.just(account))
                )
                .doOnSuccess(a -> updateCounter.increment())
                .doOnError(e -> errorCounter.increment());
    }

    public Mono<Void> handle(DeleteAccountCommand cmd) {
        log.debug("Handling DeleteAccountCommand for id: {}", cmd.id());

        return accountRepository.findById(cmd.id())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Account", "id", cmd.id())))
                .flatMap(account -> accountRepository.deleteById(account.getId())
                    .then(cacheService.evictByIdAndAll(account.getId()).then())
                )
                .doOnSuccess(v -> deleteCounter.increment())
                .doOnError(e -> errorCounter.increment());
    }
}
