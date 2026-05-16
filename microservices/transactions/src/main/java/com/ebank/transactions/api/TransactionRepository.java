package com.ebank.transactions.api;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {

    Flux<Transaction> findByFromAccountIdOrToAccountId(String fromAccountId, String toAccountId);

    Flux<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

    Flux<Transaction> findByTypeOrderByCreatedAtDesc(TransactionType type);
}
