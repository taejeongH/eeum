package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.health.dto.HealthMetricRequestDTO;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.health.repository.HealthMetricRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import org.ssafy.eeum.domain.health.dto.HeartRateRequestDTO;
import org.ssafy.eeum.domain.health.entity.HeartRate;
import org.ssafy.eeum.domain.health.repository.HeartRateRepository;
import org.ssafy.eeum.domain.iot.entity.FallEvent;
import org.ssafy.eeum.domain.iot.repository.FallEventRepository;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class HealthService {

        private final HealthMetricRepository healthMetricRepository;
        private final FamilyRepository familyRepository;
        private final org.ssafy.eeum.domain.family.repository.SupporterRepository supporterRepository;
        private final org.ssafy.eeum.global.infra.fcm.FcmService fcmService;
        private final HealthMetricPersistenceService healthMetricPersistenceService;
        private final HeartRateRepository heartRateRepository;
        private final FallEventRepository fallEventRepository;

        @Transactional
        public void saveHealthMetrics(Integer groupId, List<HealthMetricRequestDTO> requests) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                // 1. Process Real-time Heart Rate Notification (Priority)
                requests.stream()
                    .filter(req -> req.getAverageHeartRate() != null && req.getAverageHeartRate() > 0)
                    .findFirst()
                    .ifPresent(req -> {
                        log.info("Heart Rate Data Received ({} BPM). Preparing notification...", req.getAverageHeartRate());
                        try {
                            List<String> guardianTokens = supporterRepository.findAllByFamily(family).stream()
                                .filter(s -> s.getRole() != org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                                .map(s -> {
                                    if (s.getUser() != null) return s.getUser().getFcmToken();
                                    return null;
                                })
                                .filter(t -> t != null && !t.isEmpty())
                                .toList();
                            
                            if (!guardianTokens.isEmpty()) {
                                log.info("Sending notification to {} guardians.", guardianTokens.size());
                                fcmService.sendMulticast(
                                    guardianTokens,
                                    "Heart Rate",
                                    String.valueOf(req.getAverageHeartRate()),
                                    "HR_UPDATE",
                                    null,
                                    null,
                                    groupId
                                );
                            }
                        } catch (Exception e) {
                            log.error("Failed to send real-time notification.", e);
                        }
                    });

                // 2. Save to DB in a separate transaction to avoid poisoning the main one
                try {
                    log.info("Saving Health Metrics for Group ID: {}. Count: {}", groupId, requests.size());
                    List<HealthMetric> metrics = requests.stream()
                                    .map(dto -> dto.toEntity(family))
                                    .toList();
                    
                    healthMetricPersistenceService.saveAllWithNewTransaction(metrics);
                    log.info("Metrics saved to DB successfully.");
                } catch (Exception e) {
                    log.error("Failed to save health metrics to DB (Schema/Constraint Issue). Skipping save.", e);
                }
        }

        public org.ssafy.eeum.domain.health.entity.HealthMetric getPatientLatestMetrics(Integer groupId) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                // Find the PATIENT in the group to verify its existence
                supporterRepository
                                .findByFamilyAndRole(family, org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "그룹 내 피부양자를 찾을 수 없습니다."));

                // Get latest metric for this family
                return healthMetricRepository.findFirstByFamilyOrderByRecordDateDesc(family)
                                .orElse(null);
        }

        @Transactional
        public void requestMeasurement(Integer groupId) {
                log.info("Requesting Heart Rate Measurement for Group ID: {}", groupId);

                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                org.ssafy.eeum.domain.family.entity.Supporter patient = supporterRepository
                                .findByFamilyAndRole(family, org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "그룹 내 피부양자를 찾을 수 없습니다."));

                org.ssafy.eeum.domain.auth.entity.User user = patient.getUser();
                if (user == null || user.getFcmToken() == null) {
                        log.error("Patient or FCM Token is missing. User: {}, Token: {}", user != null ? user.getId() : "null", user != null ? user.getFcmToken() : "null");
                        throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "피부양자의 기기 정보(토큰)가 없습니다.");
                }

                log.info("Sending FCM Command to Patient (User ID: {}, Token: {}...)", user.getId(), user.getFcmToken().substring(0, Math.min(user.getFcmToken().length(), 10)));

                // Send High Priority FCM to Trigger Measurement
                // title, body, type, notificationId, route, familyId, groupName, eventId
                fcmService.sendMessageTo(
                                user.getFcmToken(),
                                null,
                                null,
                                "CMD_MEASURE_HR", // Important: This type must be handled by Watch
                                null,
                                null,
                                groupId,
                                null,
                                null); // No Event ID for manual measurement
                
                log.info("FCM Command Sent Successfully (Manual). Group ID: {}", groupId);
                return;
        }

        public org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO getLatestHeartRate(Integer groupId) {
             return heartRateRepository.findLatestByFamilyId(groupId)
                     .orElse(null);
        }

        public org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO getHeartRateResult(Integer eventId) {
            org.ssafy.eeum.domain.iot.entity.FallEvent event = fallEventRepository.findById(eventId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

            return heartRateRepository.findAggregatedMetricsByFallEventId(eventId);
        }

        public void requestHealthSync(Integer groupId) {
            log.info("Requesting Health Sync for Group ID: {}", groupId);

            Family family = familyRepository.findById(groupId)
                            .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                            "가족 그룹을 찾을 수 없습니다."));

            org.ssafy.eeum.domain.family.entity.Supporter patient = supporterRepository
                            .findByFamilyAndRole(family, org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                            .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                            "그룹 내 피부양자를 찾을 수 없습니다."));

            org.ssafy.eeum.domain.auth.entity.User user = patient.getUser();
            if (user == null || user.getFcmToken() == null) {
                    log.error("Patient or FCM Token is missing. User: {}, Token: {}", user != null ? user.getId() : "null", user != null ? user.getFcmToken() : "null");
                    throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "피부양자의 기기 정보(토큰)가 없습니다.");
            }

            log.info("Sending Sync FCM Command to Patient (User ID: {}, Token: {}...)", user.getId(), user.getFcmToken().substring(0, Math.min(user.getFcmToken().length(), 10)));

            // Send Sync Command
            fcmService.sendMessageTo(
                            user.getFcmToken(),
                            null,
                            null,
                            "CMD_SYNC_HEALTH",
                            null,
                            null,
                            groupId,
                            null,
                            null);
            
            log.info("Sync FCM Command Sent Successfully.");
        }

        @Transactional
    public void saveHeartRate(HeartRateRequestDTO request) {
        Family family = familyRepository.findById(request.getFamilyId())
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "가족 그룹을 찾을 수 없습니다."));

        FallEvent fallEvent = null;
        if (request.getRelatedId() != null) {
            fallEvent = fallEventRepository.findById(request.getRelatedId())
                    .orElse(null); // Optional: Link if exists, otherwise null
        }

        HeartRate heartRate = HeartRate.builder()
                .minRate(request.getMinRate())
                .maxRate(request.getMaxRate())
                .avgRate(request.getAvgRate())
                .measuredAt(request.getMeasuredAt())
                .family(family)
                .fallEvent(fallEvent)
                .build();

        heartRateRepository.save(heartRate);
        log.info("Heart Rate Saved. ID: {}, Avg: {} BPM, Family: {}, Related Event: {}", 
            heartRate.getId(), heartRate.getAvgRate(), family.getId(), fallEvent != null ? fallEvent.getId() : "None");
    }

    /**
     * Check if the latest heart rate is abnormal
     * Returns true if abnormal, false if normal or no recent data
     */
    public boolean isHeartRateAbnormal(Integer groupId) {
        return heartRateRepository.findFirstByFamilyIdOrderByMeasuredAtDesc(groupId)
                .map(hr -> {
                     // Check if data is within last 2 minutes
                     if (hr.getMeasuredAt().isBefore(java.time.LocalDateTime.now().minusMinutes(2))) {
                         log.info("Latest Heart Rate is too old to be considered for emergency: {}", hr.getMeasuredAt());
                         return false; 
                     }
                     
                     boolean abnormal = hr.getAvgRate() < 50 || hr.getAvgRate() > 120;
                     if (abnormal) {
                         log.warn("Abnormal Heart Rate Detected! Avg: {} (Threshold: <50 or >120)", hr.getAvgRate());
                     } else {
                         log.info("Heart Rate Normal. Avg: {}", hr.getAvgRate());
                     }
                     return abnormal;
                })
                .orElse(false);
    }
}
