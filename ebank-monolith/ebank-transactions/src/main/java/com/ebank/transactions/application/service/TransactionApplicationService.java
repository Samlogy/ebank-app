package com.ebank.transactions.application.service;

import com.ebank.accounts.application.command.CreditAccountCommand;
import com.ebank.accounts.application.command.DebitAccountCommand;
import com.ebank.accounts.application.port.in.CreditAccountUseCase;
import com.ebank.accounts.application.port.in.DebitAccountUseCase;
import com.ebank.transactions.application.command.CreateTransactionCommand;
import com.ebank.transactions.application.dto.TransactionDto;
import com.ebank.transactions.application.port.in.CreateTransactionUseCase;
import com.ebank.transactions.application.port.in.GetTransactionUseCase;
import com.ebank.transactions.application.port.out.TransactionRepositoryPort;
import com.ebank.transactions.domain.Transaction;
import com.ebank.transactions.domain.TransactionType;
import com.ebank.transactions.domain.event.TransactionCreatedEvent;
import com.ebank.core.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
class TransactionApplicationService implements CreateTransactionUseCase, GetTransactionUseCase {

    private final TransactionRepositoryPort transactionRepository;
    private final DebitAccountUseCase debitAccountUseCase;
    private final CreditAccountUseCase creditAccountUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry registry;

    TransactionApplicationService(TransactionRepositoryPort transactionRepository,
                                   DebitAccountUseCase debitAccountUseCase,
                                   CreditAccountUseCase creditAccountUseCase,
                                   ApplicationEventPublisher eventPublisher,
                                   MeterRegistry registry) {
        this.transactionRepository = transactionRepository;
        this.debitAccountUseCase = debitAccountUseCase;
        this.creditAccountUseCase = creditAccountUseCase;
        this.eventPublisher = eventPublisher;
        this.registry = registry;
    }

    @Override
    @Transactional
    public TransactionDto create(CreateTransactionCommand cmd) {
        String typeTag = cmd.type() != null ? cmd.type().name() : "UNKNOWN";
        Timer.Sample sample = Timer.start(registry);

        try {
            // Step 1: debit source account (only for TRANSFER / WITHDRAWAL)
            if (cmd.fromAccountId() != null
                    && (cmd.type() == TransactionType.TRANSFER || cmd.type() == TransactionType.WITHDRAWAL)) {
                debitAccountUseCase.debit(new DebitAccountCommand(cmd.fromAccountId(), cmd.amount()));
            }

            // Step 2: credit target account (only for TRANSFER / DEPOSIT)
            if (cmd.toAccountId() != null
                    && (cmd.type() == TransactionType.TRANSFER || cmd.type() == TransactionType.DEPOSIT)) {
                creditAccountUseCase.credit(new CreditAccountCommand(cmd.toAccountId(), cmd.amount()));
            }

            // Step 3: persist transaction
            Transaction tx = Transaction.create(
                    cmd.fromAccountId(), cmd.toAccountId(),
                    cmd.amount(), cmd.type(), cmd.description());
            tx.complete();
            tx = transactionRepository.save(tx);

            // Step 4: publish domain event (fires AFTER_COMMIT for Kafka/notifications)
            eventPublisher.publishEvent(TransactionCreatedEvent.from(tx));

            counter(typeTag, "success").increment();
            sample.stop(timer(typeTag));
            log.info("Transaction created: id={}, ref={}", tx.getId(), tx.getReferenceNumber());
            return toDto(tx);

        } catch (Exception e) {
            counter(typeTag, "error").increment();
            sample.stop(timer(typeTag));
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionDto getById(Long id) {
        return toDto(transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDto> getAll() {
        return transactionRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDto> getByAccountId(Long accountId) {
        return transactionRepository.findByAccountId(accountId).stream().map(this::toDto).toList();
    }

    private TransactionDto toDto(Transaction t) {
        return new TransactionDto(t.getId(), t.getFromAccountId(), t.getToAccountId(),
                t.getAmount(), t.getType(), t.getStatus(), t.getDescription(),
                t.getReferenceNumber(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private Counter counter(String type, String status) {
        return Counter.builder("transactions.operations")
                .tag("type", type).tag("status", status)
                .register(registry);
    }

    private Timer timer(String type) {
        return Timer.builder("transactions.creation.duration")
                .tag("type", type)
                .description("End-to-end duration of transaction creation")
                .register(registry);
    }
}
