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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IotNotificationService {

    private final IotNotificationRepository notificationRepository;
    private final FamilyRepository familyRepository;

    
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

    
    @Transactional
    public void markAsRead(String messageId) {
        notificationRepository.findByMessageId(messageId)
                .ifPresentOrElse(
                        IotNotification::markAsRead,
                        () -> log.warn("Notification not found for ACK: MessageId={}", messageId));
    }

    
    public Page<IotNotification> getNotifications(Integer familyId, Boolean unreadOnly, Pageable pageable) {
        if (unreadOnly != null && unreadOnly) {
            return notificationRepository.findByFamilyIdAndIsRead(familyId, false, pageable);
        }
        return notificationRepository.findByFamilyId(familyId, pageable);
    }

    
    @Transactional
    public void markAllAsRead(Integer familyId) {
        notificationRepository.markAllAsRead(familyId);
    }

    
    @Transactional
    public void deleteNotification(Long id, Integer familyId) {
        IotNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        if (!notification.getFamily().getId().equals(familyId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        notificationRepository.delete(notification);
    }

    
    @Transactional
    public void deleteAllRead(Integer familyId) {
        notificationRepository.deleteAllReadNotifications(familyId);
    }
}
