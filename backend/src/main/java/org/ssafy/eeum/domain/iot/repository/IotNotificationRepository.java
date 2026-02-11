package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.iot.entity.IotNotification;

import java.util.Optional;

/**
 * IoT 기기용 알림(IotNotification) 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 알림 목록 조회(페이징 지원) 및 일괄 읽음 처리 기능을 제공합니다.
 * 
 * @summary 기기 알림 레포지토리
 */
public interface IotNotificationRepository extends JpaRepository<IotNotification, Long> {

    /**
     * 특정 가족 그룹의 알림 목록을 페이지 단위로 조회합니다.
     * 
     * @summary 가족별 알림 목록 조회
     * @param familyId 가족 그룹 식별자
     * @param pageable 페이지네이션 정보
     * @return 알림 페이지
     */
    Page<IotNotification> findByFamilyId(Integer familyId, Pageable pageable);

    /**
     * 특정 가족 그룹의 읽음/안읽음 상태별 알림 목록을 페이지 단위로 조회합니다.
     * 
     * @summary 가족 및 상태별 알림 조회
     * @param familyId 가족 그룹 식별자
     * @param isRead   읽음 여부
     * @param pageable 페이지네이션 정보
     * @return 알림 페이지
     */
    Page<IotNotification> findByFamilyIdAndIsRead(Integer familyId, Boolean isRead, Pageable pageable);

    /**
     * 메시지 고유 식별자(message_id)를 통해 알림을 조회합니다.
     * 
     * @summary 메시지 ID 기반 알림 조회
     * @param messageId 메시지 식별자
     * @return 알림 정보 (Optional)
     */
    Optional<IotNotification> findByMessageId(String messageId);

    /**
     * 특정 가족의 읽지 않은 모든 알림을 읽음 처리합니다.
     * 
     * @summary 전체 읽음 처리
     * @param familyId 가족 그룹 식별자
     */
    @Modifying
    @Query("UPDATE IotNotification n SET n.isRead = true WHERE n.family.id = :familyId AND n.isRead = false")
    void markAllAsRead(@Param("familyId") Integer familyId);

    /**
     * 특정 가족의 읽은 모든 알림 데이터를 영구 삭제합니다.
     * 
     * @summary 읽은 알림 전체 삭제
     * @param familyId 가족 그룹 식별자
     */
    @Modifying
    @Query("DELETE FROM IotNotification n WHERE n.family.id = :familyId AND n.isRead = true")
    void deleteAllReadNotifications(@Param("familyId") Integer familyId);
}
