package org.ssafy.eeum.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IotEventService {

    private final NotificationService notificationService;
    private final FamilyRepository familyRepository;
    private final SupporterRepository supporterRepository;

    /**
     * IoT 이벤트 처리 (외출, 귀가 등)
     * 보호자 전원에게 알림을 발송합니다.
     */
    @Transactional
    public void handleIotEvent(Integer familyId, String type) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        String groupName = family.getGroupName();
        
        // Find PATIENT from supporters
        String dependentName = supporterRepository.findAllByFamily(family).stream()
                .filter(s -> s.getRole() == Supporter.Role.PATIENT)
                .findFirst()
                .map(s -> s.getUser().getName())
                .orElse("피부양자");
        
        String message;
        String title;

        if ("OUTING".equalsIgnoreCase(type)) {
            title = groupName + " - " + dependentName;
            message = dependentName + "님이 외출하셨습니다.";
        } else if ("RETURN".equalsIgnoreCase(type)) {
            title = groupName + " - " + dependentName;
            message = dependentName + "님이 귀가하셨습니다.";
        } else {
            title = groupName + " - " + dependentName;
            message = "활동이 감지되었습니다.";
        }

        log.info("Handling IoT Event: FamilyID={}, Type={}, Group={}, Dependent={}", 
                familyId, type, groupName, dependentName);

        // 모든 보호자(CAREGIVER) 조회
        List<Supporter> caregivers = supporterRepository.findAllByFamily(family).stream()
                .filter(s -> s.getRole() == Supporter.Role.CAREGIVER)
                .toList();

        if (caregivers.isEmpty()) {
            log.warn("No caregivers found for family ID: {}", familyId);
            return;
        }

        // 1. 알림 기록 생성 (한 번만 생성 여부는 기획에 따라 다름, 여기선 개별 발송이므로 구조상 메시지 생성 후 전송)
        // FallDetectionService에서는 Notification 엔티티를 하나 만들고 배달(Delivery)을 여러개 만들었음.
        // 하지만 NotificationService.createNotification은 Notification 객체 하나를 만들고 ID를 반환함.
        // 그리고 sendNotification은 NotificationDelivery를 만듦.
        // 여기서는 "같은 사건"에 대해 모든 보호자에게 알리는 것이므로 Notification은 하나여야 함.
        
        Long notificationId = notificationService.createNotification(familyId, title, message, "ACTIVITY");

        // 2. 모든 보호자에게 전송
        for (Supporter caregiver : caregivers) {
            log.info("Sending IoT Notification to Caregiver: {}", caregiver.getUser().getName());
            notificationService.sendNotification(notificationId, caregiver.getUser().getId());
        }
    }
}
