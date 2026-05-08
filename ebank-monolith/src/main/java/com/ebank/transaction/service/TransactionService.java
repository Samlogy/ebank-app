package com.ebank.transaction.service;

import com.ebank.account.entity.Account;
import com.ebank.account.repository.AccountRepository;
import com.ebank.common.exception.ResourceNotFoundException;
import com.ebank.transaction.dto.TransactionResponse;
import com.ebank.transaction.dto.TransferRequest;
import com.ebank.transaction.entity.Transaction;
import com.ebank.transaction.entity.Transaction.TransactionStatus;
import com.ebank.transaction.entity.Transaction.TransactionType;
import com.ebank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    
    @Transactional
    public TransactionResponse transfer(Long fromAccountId, TransferRequest request, Long userId) {
        Account fromAccount = accountRepository.findByIdAndUserId(fromAccountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Sender account not found"));
        
        Account toAccount = accountRepository.findById(request.getToAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Receiver account not found"));
        
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Transfer rejected: fromAccount={} balance={} requested={} reason=insufficient_funds",
                    fromAccountId, fromAccount.getBalance(), request.getAmount());
            throw new IllegalStateException("Insufficient balance");
        }

        log.info("Transfer initiated: fromAccount={} toAccount={} amount={}",
                fromAccountId, request.getToAccountId(), request.getAmount());

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        
        Transaction transaction = Transaction.builder()
            .fromAccountId(fromAccountId)
            .toAccountId(request.getToAccountId())
            .amount(request.getAmount())
            .transactionType(TransactionType.TRANSFER)
            .status(TransactionStatus.COMPLETED)
            .reference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .description(request.getDescription())
            .build();
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transfer completed: txnId={} ref={} fromAccount={} toAccount={} amount={}",
                savedTransaction.getId(), savedTransaction.getReference(),
                fromAccountId, request.getToAccountId(), request.getAmount());
        return mapToResponse(savedTransaction);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(Long accountId, Long userId) {
        accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        return transactionRepository.findByFromAccountId(accountId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
            .id(transaction.getId())
            .fromAccountId(transaction.getFromAccountId())
            .toAccountId(transaction.getToAccountId())
            .amount(transaction.getAmount())
            .transactionType(transaction.getTransactionType())
            .status(transaction.getStatus())
            .reference(transaction.getReference())
            .createdAt(transaction.getCreatedAt())
            .build();
    }
}
