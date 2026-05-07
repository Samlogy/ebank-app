package com.ebank.transactions.application.port.out;

import com.ebank.transactions.domain.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepositoryPort {
    Optional<Transaction> findById(Long id);
    List<Transaction> findAll();
    List<Transaction> findByAccountId(Long accountId);
    Transaction save(Transaction transaction);
}
