package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.entity.IotNotification;
import org.ssafy.eeum.domain.iot.repository.IotNotificationRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

/**
 * IoT 기기로부터 발생하거나 기기용으로 생성된 알림 이력을 관리하는 서비스 클래스입니다.
 * MongoDB(또는 RDS)에 알림 로그를 저장하고, MQTT 기반의 ACK 처리를 통한 읽음 상태 동기화를 담당합니다.
 * 
 * @summary IoT 알림 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IotNotificationService {

    private final IotNotificationRepository notificationRepository;
    private final FamilyRepository familyRepository;

    /**
     * 새로운 알림 로그를 저장합니다. 주로 MQTT로 알림을 발송하기 전/후에 호출됩니다.
     * 
     * @summary 알림 로그 저장
     * @param serialNumber 기기 시리얼 번호
     * @param groupId      가족 그룹 식별자
     * @param kind         알림 종류 (IMAGE, VOICE 등)
     * @param messageId    중복 방지 및 추적을 위한 메시지 식별자
     * @param content      알림 내용 요약
     */
    @Transactional
    public void saveNotification(String serialNumber, Integer groupId, String kind, String messageId, String content) {
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        IotNotification notification = IotNotification.builder()
                .serialNumber(serialNumber)
                .family(family)
                .kind(kind)
                .messageId(messageId)
                .content(content)
                .build();

        notificationRepository.save(notification);
    }

    /**
     * 메시지 식별자를 기반으로 특정 알림을 읽음 상태로 변경합니다.
     * IoT 기기에서 MQTT ACK를 보내왔을 때 호출됩니다.
     * 
     * @summary 알림 읽음 처리 (MQTT ACK 연동)
     * @param messageId 알림 메시지 식별자
     */
    @Transactional
    public void markAsRead(String messageId) {
        notificationRepository.findByMessageId(messageId)
                .ifPresentOrElse(
                        IotNotification::markAsRead,
                        () -> log.warn("[ACK] 알림 메시지({})를 찾을 수 없어 읽음 처리에 실패했습니다.", messageId));
    }

    /**
     * 특정 가족 그룹의 전체 알림 이력을 페이지 단위로 조회합니다.
     * 
     * @summary 가족별 알림 목록 조회
     * @param familyId   가족 그룹 식별자
     * @param unreadOnly 안읽은 알림만 필터링 여부
     * @param pageable   페이지네이션 정보
     * @return 알림 엔티티 페이지
     */
    public Page<IotNotification> getNotifications(Integer familyId, Boolean unreadOnly, Pageable pageable) {
        if (unreadOnly != null && unreadOnly) {
            return notificationRepository.findByFamilyIdAndIsRead(familyId, false, pageable);
        }
        return notificationRepository.findByFamilyId(familyId, pageable);
    }

    /**
     * 특정 가족 그룹의 모든 안읽은 알림을 일괄 읽음 처리합니다.
     * 
     * @summary 전체 알림 읽음 처리
     * @param familyId 가족 그룹 식별자
     */
    @Transactional
    public void markAllAsRead(Integer familyId) {
        notificationRepository.markAllAsRead(familyId);
    }

    /**
     * 특정 알림 이력을 영구 삭제합니다.
     * 요청한 사용자의 가족 그룹과 알림의 그룹 정보가 일치하는지 검증합니다.
     * 
     * @summary 알림 이력 단건 삭제
     * @param id       삭제 대상 알림 ID
     * @param familyId 가족 그룹 식별자 (검증용)
     */
    @Transactional
    public void deleteNotification(Long id, Integer familyId) {
        IotNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        if (!notification.getFamily().getId().equals(familyId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        notificationRepository.delete(notification);
    }

    /**
     * 특정 가족 그룹 내에서 이미 읽은 모든 알림 이력을 일괄 삭제합니다.
     * 
     * @summary 읽은 알림 전체 삭제
     * @param familyId 가족 그룹 식별자
     */
    @Transactional
    public void deleteAllRead(Integer familyId) {
        notificationRepository.deleteAllReadNotifications(familyId);
    }
}
