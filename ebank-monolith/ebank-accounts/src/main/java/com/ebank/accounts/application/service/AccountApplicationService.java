package com.ebank.accounts.application.service;

import com.ebank.accounts.application.command.*;
import com.ebank.accounts.application.dto.AccountDto;
import com.ebank.accounts.application.port.in.*;
import com.ebank.accounts.application.port.out.AccountCachePort;
import com.ebank.accounts.application.port.out.AccountRepositoryPort;
import com.ebank.accounts.domain.Account;
import com.ebank.core.exception.BusinessRuleViolationException;
import com.ebank.core.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
class AccountApplicationService implements CreateAccountUseCase, UpdateAccountUseCase,
        DeleteAccountUseCase, GetAccountUseCase, DebitAccountUseCase, CreditAccountUseCase {

    private final AccountRepositoryPort accountRepository;
    private final AccountCachePort cache;
    private final Counter createCounter;
    private final Counter updateCounter;
    private final Counter deleteCounter;
    private final Counter errorCounter;

    AccountApplicationService(AccountRepositoryPort accountRepository,
                               AccountCachePort cache,
                               MeterRegistry registry) {
        this.accountRepository = accountRepository;
        this.cache = cache;
        this.createCounter = counter(registry, "create", "success");
        this.updateCounter = counter(registry, "update", "success");
        this.deleteCounter = counter(registry, "delete", "success");
        this.errorCounter = counter(registry, "any", "error");
    }

    @Override
    @Transactional
    public AccountDto create(CreateAccountCommand cmd) {
        if (accountRepository.existsByAccountNumber(cmd.accountNumber())) {
            throw new BusinessRuleViolationException("Account number already exists: " + cmd.accountNumber());
        }
        if (accountRepository.existsByEmail(cmd.email())) {
            throw new BusinessRuleViolationException("Email already exists: " + cmd.email());
        }
        Account account = Account.builder()
                .accountNumber(cmd.accountNumber())
                .accountHolderName(cmd.accountHolderName())
                .email(cmd.email())
                .phoneNumber(cmd.phoneNumber())
                .accountType(cmd.accountType())
                .balance(cmd.balance())
                .address(cmd.address())
                .status(cmd.status())
                .build();
        account = accountRepository.save(account);
        cache.evictAll();
        createCounter.increment();
        log.info("Account created: {}", account.getAccountNumber());
        return toDto(account);
    }

    @Override
    @Transactional
    public AccountDto update(UpdateAccountCommand cmd) {
        Account account = accountRepository.findById(cmd.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", cmd.id()));
        if (accountRepository.existsByEmailAndIdNot(cmd.email(), cmd.id())) {
            throw new BusinessRuleViolationException("Email already in use: " + cmd.email());
        }
        account.update(cmd.accountHolderName(), cmd.email(), cmd.phoneNumber(),
                cmd.accountType(), cmd.balance(), cmd.address(), cmd.status());
        account = accountRepository.save(account);
        cache.evictById(cmd.id());
        cache.evictAll();
        updateCounter.increment();
        return toDto(account);
    }

    @Override
    @Transactional
    public void delete(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        accountRepository.deleteById(account.getId());
        cache.evictById(accountId);
        cache.evictAll();
        deleteCounter.increment();
        log.info("Account deleted: {}", accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getById(Long id) {
        return cache.findById(id).orElseGet(() -> {
            AccountDto dto = toDto(accountRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id)));
            cache.cacheById(dto);
            return dto;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> getAll() {
        return cache.findAll().orElseGet(() -> {
            List<AccountDto> all = accountRepository.findAll().stream().map(this::toDto).toList();
            cache.cacheAll(all);
            return all;
        });
    }

    @Override
    @Transactional
    public void debit(DebitAccountCommand cmd) {
        Account account = accountRepository.findById(cmd.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", cmd.accountId()));
        account.withdraw(cmd.amount());
        accountRepository.save(account);
        cache.evictById(cmd.accountId());
        log.debug("Debited {} from account {}", cmd.amount(), cmd.accountId());
    }

    @Override
    @Transactional
    public void credit(CreditAccountCommand cmd) {
        Account account = accountRepository.findById(cmd.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", cmd.accountId()));
        account.deposit(cmd.amount());
        accountRepository.save(account);
        cache.evictById(cmd.accountId());
        log.debug("Credited {} to account {}", cmd.amount(), cmd.accountId());
    }

    private AccountDto toDto(Account a) {
        return new AccountDto(a.getId(), a.getAccountNumber(), a.getAccountHolderName(),
                a.getEmail(), a.getPhoneNumber(), a.getAccountType(), a.getBalance(),
                a.getAddress(), a.getStatus(), a.getCreatedAt(), a.getUpdatedAt());
    }

    private static Counter counter(MeterRegistry registry, String operation, String status) {
        return Counter.builder("accounts.operations")
                .tag("operation", operation).tag("status", status)
                .register(registry);
    }
}
