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

        List<Supporter> caregivers = supporterRepository.findAllByFamily(family).stream()
                .filter(s -> s.getRole() == Supporter.Role.CAREGIVER)
                .toList();

        if (caregivers.isEmpty()) {
            log.warn("No caregivers found for family ID: {}", familyId);
            return;
        }
        
        Long notificationId = notificationService.createNotification(familyId, title, message, "ACTIVITY", null);

        for (Supporter caregiver : caregivers) {
            log.info("Sending IoT Notification to Caregiver: {}", caregiver.getUser().getName());
            notificationService.sendNotification(notificationId, caregiver.getUser().getId());
        }
    }
}
