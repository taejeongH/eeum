package org.ssafy.eeum.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.notification.entity.Notification;

import java.util.List;
import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByFamilyIdOrderByCreatedAtDesc(Integer familyId);
    List<Notification> findByFamilyIdAndCreatedAtAfterOrderByCreatedAtDesc(Integer familyId, LocalDateTime dateTime);
}
