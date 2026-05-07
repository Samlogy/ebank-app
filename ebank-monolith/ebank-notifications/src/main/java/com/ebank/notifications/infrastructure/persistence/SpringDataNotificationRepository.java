package com.ebank.notifications.infrastructure.persistence;

import com.ebank.notifications.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataNotificationRepository extends JpaRepository<Notification, Long> {}
