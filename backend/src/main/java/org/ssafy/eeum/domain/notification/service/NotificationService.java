package org.ssafy.eeum.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.notification.dto.NotificationHistoryResponseDto;
import org.ssafy.eeum.domain.notification.entity.Notification;
import org.ssafy.eeum.domain.notification.entity.NotificationDelivery;
import org.ssafy.eeum.domain.notification.repository.NotificationDeliveryRepository;
import org.ssafy.eeum.domain.notification.repository.NotificationRepository;
import org.ssafy.eeum.domain.iot.entity.FallEvent;
import org.ssafy.eeum.domain.iot.repository.FallEventRepository;
import org.ssafy.eeum.global.infra.s3.S3Service;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.fcm.FcmService;
import org.ssafy.eeum.global.infra.fcm.FcmUnregisteredTokenException;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

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
        private final FallEventRepository fallEventRepository;
        private final S3Service s3Service;

        @Transactional
        public Long createNotification(Integer familyId, String title, String message, String type, Integer relatedId) {
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                Notification notification = Notification.builder()
                                .family(family)
                                .title(title)
                                .message(message)
                                .type(type)
                                .relatedId(relatedId)
                                .build();

                notificationRepository.save(notification);
                return notification.getId();
        }

        @Transactional
        public void sendNotification(Long notificationId, Integer targetUserId) {
                Notification notification = notificationRepository.findById(notificationId)
                                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

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
                        String groupName = notification.getFamily().getGroupName();

                        if ("EMERGENCY".equalsIgnoreCase(notification.getType())) {
                                route = "/families/" + familyId + "/emergency";
                        } else if ("ACTIVITY".equalsIgnoreCase(notification.getType())) {
                                route = "/families/" + familyId + "/activity";
                        }

                        log.info("FCM Debug: Calculated Route: '{}'", route);

                        try {
                                fcmService.sendMessageTo(token, notification.getTitle(), notification.getMessage(),
                                                notification.getType(), notification.getId(), route, familyId,
                                                groupName, notification.getRelatedId());
                                delivery.updateSentAt();
                        } catch (FcmUnregisteredTokenException e) {
                                log.warn("FCM token unregistered for user {}. Clearing token.", targetUserId);
                                user.updateFcmToken(null);
                        }
                }
        }

        @Transactional
        public void markAsRead(Long notificationId, Integer userId) {
                if (notificationId == null || userId == null) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "알림 ID 또는 유저 ID가 누락되었습니다.");
                }
                log.info("Processing markAsRead: NotificationID={}, UserID={}", notificationId, userId);
                Notification notification = notificationRepository.findById(notificationId)
                                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                NotificationDelivery delivery = notificationDeliveryRepository
                                .findByUserAndNotification(user, notification)
                                .orElseGet(() -> {
                                        
                                        return NotificationDelivery.builder()
                                                        .notification(notification)
                                                        .user(user)
                                                        .channel("SYSTEM")
                                                        .build();
                                });

                log.info("Found Delivery: ID={}, Current IsRead={}", delivery.getId(), delivery.isRead());

                delivery.markAsRead();
        }

        public boolean isAnyRead(Long notificationId) {
                return notificationDeliveryRepository.existsByNotificationIdAndIsReadTrue(notificationId);
        }

        public List<NotificationHistoryResponseDto> getNotificationHistory(Integer familyId, Integer userId) {
                LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
                List<Notification> notifications = notificationRepository
                                .findByFamilyIdAndCreatedAtAfterOrderByCreatedAtDesc(familyId, oneWeekAgo);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                return notifications.stream()
                                .map(notification -> {
                                        String videoUrl = null;
                                        Double confidence = null;
                                        boolean isRead = notificationDeliveryRepository
                                                        .findByUserAndNotification(user, notification)
                                                        .map(NotificationDelivery::isRead)
                                                        .orElse(false);

                                        if ("EMERGENCY".equalsIgnoreCase(notification.getType())
                                                        && notification.getRelatedId() != null) {
                                                try {
                                                        FallEvent event = fallEventRepository.findById(
                                                                        notification.getRelatedId())
                                                                        .orElse(null);
                                                        if (event != null) {
                                                                confidence = event.getConfidence();
                                                                if (event.getVideoStatus() == FallEvent.VideoStatus.SUCCESS
                                                                                && event.getVideoPath() != null) {
                                                                        videoUrl = s3Service.getPresignedUrl(
                                                                                        event.getVideoPath());
                                                                }
                                                        }
                                                } catch (Exception e) {

                                                }
                                        }

                                        return NotificationHistoryResponseDto.builder()
                                                        .id(notification.getId())
                                                        .title(notification.getTitle())
                                                        .message(notification.getMessage())
                                                        .type(notification.getType())
                                                        .relatedId(notification.getRelatedId())
                                                        .createdAt(notification.getCreatedAt())
                                                        .isRead(isRead)
                                                        .videoUrl(videoUrl)
                                                        .confidence(confidence)
                                                        .build();
                                })
                                .collect(Collectors.toList());
        }
}
