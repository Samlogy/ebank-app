package com.ebank.notifications.application.service;

import com.ebank.notifications.application.port.out.EmailPort;
import com.ebank.notifications.application.port.out.NotificationRepositoryPort;
import com.ebank.notifications.domain.Notification;
import com.ebank.transactions.domain.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private final EmailPort emailPort;
    private final NotificationRepositoryPort notificationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        String subject = "Transaction Confirmation — Ref: " + event.referenceNumber();
        String body = buildBody(event);

        Notification notification = Notification.builder()
                .recipientEmail(resolveRecipient(event))
                .subject(subject)
                .body(body)
                .transactionId(event.transactionId())
                .build();

        try {
            emailPort.send(notification.getRecipientEmail(), subject, body);
            notification.markSent();
            log.info("Notification sent for transaction ref={}", event.referenceNumber());
        } catch (Exception e) {
            notification.markFailed();
            log.error("Failed to send notification for transaction ref={}: {}", event.referenceNumber(), e.getMessage());
        } finally {
            notificationRepository.save(notification);
        }
    }

    private String resolveRecipient(TransactionCreatedEvent event) {
        // In a real system, look up the account owner's email from an AccountQueryService
        return "noreply@ebank.com";
    }

    private String buildBody(TransactionCreatedEvent event) {
        return String.format("""
                Transaction %s of amount %s has been %s.
                Reference: %s
                """,
                event.type(), event.amount(), event.status(), event.referenceNumber());
    }
}
