package org.ssafy.eeum.domain.notification.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.notification.entity.Notification;
import org.ssafy.eeum.domain.notification.entity.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    Optional<NotificationDelivery> findByUserAndNotification(User user, Notification notification);
    boolean existsByNotificationIdAndIsReadTrue(Long notificationId);
}
