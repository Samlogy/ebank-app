package com.ebank.transactions.api;

import com.ebank.transactions.cache.TransactionCacheService;
import com.ebank.transactions.dto.TransactionRequest;
import com.ebank.transactions.dto.TransactionResponse;
import com.ebank.transactions.event.TransactionEvent;
import com.ebank.transactions.event.TransactionEventPublisher;
import com.ebank.transactions.exception.TransactionNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher eventPublisher;
    private final MeterRegistry registry;

    @Nullable
    private final TransactionCacheService cacheService;

    public TransactionService(
            TransactionRepository transactionRepository,
            TransactionEventPublisher eventPublisher,
            MeterRegistry registry,
            @Autowired(required = false) TransactionCacheService cacheService) {
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.registry = registry;
        this.cacheService = cacheService;
    }

    public Flux<TransactionResponse> getAllTransactions() {
        log.info("Fetching all transactions");
        return transactionRepository.findAll()
                .map(this::mapToResponse);
    }

    public Mono<TransactionResponse> getTransactionById(String id) {
        log.info("Fetching transaction with id: {}", id);

        Mono<Transaction> fromDb = transactionRepository.findById(id)
                .switchIfEmpty(Mono.error(new TransactionNotFoundException("Transaction not found with id: " + id)));

        if (cacheService == null) {
            return fromDb.map(this::mapToResponse);
        }

        return cacheService.getCachedById(id)
                .switchIfEmpty(
                        fromDb.flatMap(t -> cacheService.putById(t).thenReturn(t))
                )
                .map(this::mapToResponse);
    }

    public Flux<TransactionResponse> getTransactionsByAccount(String accountId) {
        log.info("Fetching transactions for accountId: {}", accountId);

        Flux<Transaction> fromDb = transactionRepository.findByFromAccountIdOrToAccountId(accountId, accountId);

        if (cacheService == null) {
            return fromDb.map(this::mapToResponse);
        }

        return cacheService.getCachedByAccount(accountId)
                .flatMapMany(Flux::fromIterable)
                .switchIfEmpty(
                        fromDb.collectList()
                                .flatMapMany(list ->
                                        cacheService.putByAccount(accountId, list)
                                                .thenMany(Flux.fromIterable(list)))
                )
                .map(this::mapToResponse);
    }

    public Mono<TransactionResponse> createTransaction(TransactionRequest request) {
        log.info("Creating transaction of type={} amount={}", request.getType(), request.getAmount());

        // Start timer before the reactive chain — record on terminal signal
        Timer.Sample sample = Timer.start(registry);
        String typeTag = request.getType() != null ? request.getType().name() : "UNKNOWN";

        Transaction transaction = Transaction.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .type(request.getType())
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .referenceNumber(UUID.randomUUID().toString())
                .build();

        return transactionRepository.save(transaction)
                .flatMap(saved -> {
                    saved.setStatus(TransactionStatus.COMPLETED);
                    return transactionRepository.save(saved);
                })
                .flatMap(saved -> {
                    TransactionEvent event = new TransactionEvent(
                            "TRANSACTION_CREATED",
                            saved.getId(),
                            saved.getFromAccountId(),
                            saved.getToAccountId(),
                            saved.getAmount(),
                            saved.getType().name(),
                            saved.getStatus().name(),
                            LocalDateTime.now()
                    );
                    eventPublisher.publish(event);
                    log.info("Transaction created: id={}, ref={}", saved.getId(), saved.getReferenceNumber());

                    if (cacheService != null) {
                        return cacheService.putById(saved).thenReturn(mapToResponse(saved));
                    }
                    return Mono.just(mapToResponse(saved));
                })
                .doOnSuccess(r -> {
                    // Count by transaction type — e.g. {type="TRANSFER", status="success"}
                    Counter.builder("transactions.operations")
                            .tag("type", typeTag).tag("status", "success")
                            .description("Number of transactions created by type")
                            .register(registry).increment();
                    sample.stop(Timer.builder("transactions.creation.duration")
                            .tag("type", typeTag)
                            .description("End-to-end duration of transaction creation (DB save + Kafka publish)")
                            .register(registry));
                })
                .doOnError(e -> {
                    Counter.builder("transactions.operations")
                            .tag("type", typeTag).tag("status", "error")
                            .register(registry).increment();
                    sample.stop(Timer.builder("transactions.creation.duration")
                            .tag("type", typeTag)
                            .register(registry));
                });
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .referenceNumber(transaction.getReferenceNumber())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
