package com.ebank.account.service;

import com.ebank.account.dto.AccountResponse;
import com.ebank.account.dto.CreateAccountRequest;
import com.ebank.account.entity.Account;
import com.ebank.account.repository.AccountRepository;
import com.ebank.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "accounts", key = "#userId"),
        @CacheEvict(value = "account",  allEntries = true)
    })
    public AccountResponse createAccount(Long userId, CreateAccountRequest request) {
        String accountNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Account account = Account.builder()
            .userId(userId)
            .accountNumber(accountNumber)
            .accountType(request.getAccountType())
            .balance(BigDecimal.ZERO)
            .status(Account.AccountStatus.ACTIVE)
            .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Account created: accountId={} userId={} type={} number={}",
                savedAccount.getId(), userId, request.getAccountType(), accountNumber);
        return mapToResponse(savedAccount);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "accounts", key = "#userId")
    public List<AccountResponse> getUserAccounts(Long userId) {
        log.debug("Cache miss — loading accounts from DB: userId={}", userId);
        return accountRepository.findByUserId(userId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "account", key = "#accountId")
    public AccountResponse getAccount(Long accountId, Long userId) {
        log.debug("Cache miss — loading account from DB: accountId={}", accountId);
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return mapToResponse(account);
    }

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
            .id(account.getId())
            .accountNumber(account.getAccountNumber())
            .accountType(account.getAccountType())
            .balance(account.getBalance())
            .status(account.getStatus())
            .createdAt(account.getCreatedAt())
            .build();
    }
}
