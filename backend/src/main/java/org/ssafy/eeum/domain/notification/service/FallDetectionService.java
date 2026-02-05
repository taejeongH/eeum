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

    public void handleFallDetection(Integer familyId, String message, Integer eventId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        String groupName = family.getGroupName();

        String dependentName = supporterRepository.findAllByFamily(family).stream()
                .filter(s -> s.getRole() == Supporter.Role.PATIENT)
                .findFirst()
                .map(s -> s.getUser().getName())
                .orElse("피부양자");
                
        String title = groupName + " - " + dependentName;
        String body = dependentName + "님에게 낙상이 감지되었습니다!";

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

        Long notificationId = notificationService.createNotification(familyId, title, body, "EMERGENCY", eventId);
        notifyCaregiver(caregivers, 0, notificationId);
    }

    private void notifyCaregiver(List<CaregiverInfo> caregivers, int index, Long notificationId) {
        if (index >= caregivers.size()) {
            return;
        }

        try {
            // 이미 누군가 읽었다면 중단
            if (notificationService.isAnyRead(notificationId)) {
                return;
            }

            CaregiverInfo currentCaregiver = caregivers.get(index);
            notificationService.sendNotification(notificationId, currentCaregiver.userId());

            // 30초 후 다음 보호자 확인/알림 예약
            scheduler.schedule(() -> {
                try {
                    notifyCaregiver(caregivers, index + 1, notificationId);
                } catch (Exception e) {

                }
            }, 30, TimeUnit.SECONDS);

        } catch (Exception e) {

        }
    }

    private record CaregiverInfo(Integer userId, String userName) {}
}
