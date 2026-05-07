package com.ebank.transactions.infrastructure.persistence;

import com.ebank.transactions.application.port.out.TransactionRepositoryPort;
import com.ebank.transactions.domain.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class TransactionRepositoryAdapter implements TransactionRepositoryPort {

    private final SpringDataTransactionRepository delegate;

    @Override
    public Optional<Transaction> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Transaction> findAll() {
        return delegate.findAll();
    }

    @Override
    public List<Transaction> findByAccountId(Long accountId) {
        return delegate.findByAccountId(accountId);
    }

    @Override
    public Transaction save(Transaction transaction) {
        return delegate.save(transaction);
    }
}
