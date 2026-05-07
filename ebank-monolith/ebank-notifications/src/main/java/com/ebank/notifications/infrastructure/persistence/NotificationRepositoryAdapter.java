package com.ebank.notifications.infrastructure.persistence;

import com.ebank.notifications.application.port.out.NotificationRepositoryPort;
import com.ebank.notifications.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final SpringDataNotificationRepository delegate;

    @Override
    public Notification save(Notification notification) {
        return delegate.save(notification);
    }
}
