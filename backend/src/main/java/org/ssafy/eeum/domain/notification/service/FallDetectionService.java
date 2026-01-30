package org.ssafy.eeum.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FallDetectionService {

    private final NotificationService notificationService;
    private final FamilyRepository familyRepository;
    private final SupporterRepository supporterRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void handleFallDetection(Integer familyId, String message) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        List<CaregiverInfo> caregivers = supporterRepository.findAllByFamily(family).stream()
                .filter(s -> s.getRole() == Supporter.Role.CAREGIVER)
                .filter(s -> s.getEmergencyPriority() != null && s.getEmergencyPriority() >= 1 && s.getEmergencyPriority() <= 3)
                .sorted(Comparator.comparingInt(s -> s.getEmergencyPriority()))
                .map(s -> new CaregiverInfo(s.getUser().getId(), s.getUser().getName())) // Extract needed data eagerly
                .collect(Collectors.toList());

        if (caregivers.isEmpty()) {
            log.warn("No caregivers found for family ID: {}", familyId);
            return;
        }

        // 1. 알림 기록 생성 (한 번만)
        Long notificationId = notificationService.createNotification(familyId, "낙상 감지!", message, "EMERGENCY");

        // 2. 단계적 알림 프로세스 시작
        notifyCaregiver(caregivers, 0, notificationId);
    }

    private void notifyCaregiver(List<CaregiverInfo> caregivers, int index, Long notificationId) {
        if (index >= caregivers.size()) {
            log.info("Finished notifying all caregivers for notification ID: {}", notificationId);
            return;
        }

        try {
            // 이미 누군가 읽었다면 중단 (다음 발송 전 확인)
            if (notificationService.isAnyRead(notificationId)) {
                log.info("Notification ID: {} was read. Stopping escalation.", notificationId);
                return;
            }

            CaregiverInfo currentCaregiver = caregivers.get(index);
            log.info("Sending Fall Notification to Priority {}: {}", index + 1, currentCaregiver.userName());

            notificationService.sendNotification(notificationId, currentCaregiver.userId());

            // 30초 후 다음 보호자 확인/알림 예약
            scheduler.schedule(() -> {
                try {
                    notifyCaregiver(caregivers, index + 1, notificationId);
                } catch (Exception e) {
                    log.error("Error during scheduled fall detection escalation", e);
                }
            }, 30, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error sending initial notification in escalation loop", e);
        }
    }

    private record CaregiverInfo(Integer userId, String userName) {}
}
