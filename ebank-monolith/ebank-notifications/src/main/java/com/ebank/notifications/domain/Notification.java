package com.ebank.notifications.domain;

import com.ebank.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Notification extends BaseEntity {

    @Column(name = "recipient_email", nullable = false, length = 100)
    private String recipientEmail;

    @Column(length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Builder
    Notification(String recipientEmail, String subject, String body, Long transactionId) {
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.status = NotificationStatus.PENDING;
        this.transactionId = transactionId;
    }

    void markSent() {
        this.status = NotificationStatus.SENT;
    }

    void markFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
