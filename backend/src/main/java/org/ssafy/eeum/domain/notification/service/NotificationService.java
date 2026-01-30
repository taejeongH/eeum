package org.ssafy.eeum.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.notification.controller.NotificationController;
import org.ssafy.eeum.domain.notification.dto.NotificationHistoryResponseDto;
import org.ssafy.eeum.domain.notification.entity.Notification;
import org.ssafy.eeum.domain.notification.entity.NotificationDelivery;
import org.ssafy.eeum.domain.notification.repository.NotificationDeliveryRepository;
import org.ssafy.eeum.domain.notification.repository.NotificationRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.fcm.FcmService;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final FcmService fcmService;

    @Transactional
    public Long createNotification(Integer familyId, String title, String message, String type) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .family(family)
                .title(title)
                .message(message)
                .type(type)
                .build();

        notificationRepository.save(notification);
        return notification.getId();
    }

    @Transactional
    public void sendNotification(Long notificationId, Integer targetUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        NotificationDelivery delivery = NotificationDelivery.builder()
                .notification(notification)
                .user(user)
                .channel("FCM")
                .build();

        notificationDeliveryRepository.save(delivery);

        String token = user.getFcmToken();
        if (token != null && !token.isEmpty()) {
            String route = null;
            Integer familyId = notification.getFamily().getId();
            
            log.info("FCM Debug: Notification Type from DB: '{}'", notification.getType());
            
            if ("EMERGENCY".equalsIgnoreCase(notification.getType())) {
                route = "/families/" + familyId + "/emergency";
            } else if ("ACTIVITY".equalsIgnoreCase(notification.getType())) {
                route = "/families/" + familyId + "/activity";
            }
            
            log.info("FCM Debug: Calculated Route: '{}'", route);
            
            fcmService.sendMessageTo(token, notification.getTitle(), notification.getMessage(), notification.getType(), notification.getId(), route);
            delivery.updateSentAt();
        }
    }

    @Transactional
    public void markAsRead(Long notificationId, Integer userId) {
        log.info("Processing markAsRead: NotificationID={}, UserID={}", notificationId, userId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)); // TODO: Add Notification Not Found error

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        NotificationDelivery delivery = notificationDeliveryRepository.findByUserAndNotification(user, notification)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)); // TODO: Delivery Not Found
        
        log.info("Found Delivery: ID={}, Current IsRead={}", delivery.getId(), delivery.isRead());

        delivery.markAsRead();
        log.info("Updated Delivery IsRead to true");
    }

    public boolean isAnyRead(Long notificationId) {
        return notificationDeliveryRepository.existsByNotificationIdAndIsReadTrue(notificationId);
    }

    public List<NotificationHistoryResponseDto> getNotificationHistory(Integer familyId, Integer userId) {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
        java.util.List<Notification> notifications = notificationRepository.findByFamilyIdAndCreatedAtAfterOrderByCreatedAtDesc(familyId, oneWeekAgo);
        
        return notifications.stream()
                .map(notification -> {
                    
                    return NotificationHistoryResponseDto.builder()
                            .id(notification.getId())
                            .title(notification.getTitle())
                            .message(notification.getMessage())
                            .type(notification.getType())
                            .createdAt(notification.getCreatedAt())
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
