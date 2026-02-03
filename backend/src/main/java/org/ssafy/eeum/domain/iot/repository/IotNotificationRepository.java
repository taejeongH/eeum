package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.iot.entity.IotNotification;

import java.util.Optional;

public interface IotNotificationRepository extends JpaRepository<IotNotification, Long> {

    Page<IotNotification> findByFamilyId(Integer familyId, Pageable pageable);

    Page<IotNotification> findByFamilyIdAndIsRead(Integer familyId, Boolean isRead, Pageable pageable);

    Optional<IotNotification> findByMessageId(String messageId);

    @Modifying
    @Query("UPDATE IotNotification n SET n.isRead = true WHERE n.family.id = :familyId AND n.isRead = false")
    void markAllAsRead(@Param("familyId") Integer familyId);

    @Modifying
    @Query("DELETE FROM IotNotification n WHERE n.family.id = :familyId AND n.isRead = true")
    void deleteAllReadNotifications(@Param("familyId") Integer familyId);
}
