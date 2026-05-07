package com.ebank.notifications.application.port.out;

import com.ebank.notifications.domain.Notification;

public interface NotificationRepositoryPort {
    Notification save(Notification notification);
}
