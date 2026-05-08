package com.ebank.account.service;

import com.ebank.account.dto.AccountResponse;
import com.ebank.account.dto.CreateAccountRequest;
import com.ebank.account.entity.Account;
import com.ebank.account.repository.AccountRepository;
import com.ebank.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final AccountRepository accountRepository;
    
    @Transactional
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
        return mapToResponse(savedAccount);
    }
    
    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(Long userId) {
        return accountRepository.findByUserId(userId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId, Long userId) {
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
