package org.ssafy.eeum.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
