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

import org.ssafy.eeum.global.infra.fcm.FcmUnregisteredTokenException;

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



                // 2. Save to DB in a separate transaction to avoid poisoning the main one
                try {
                    List<HealthMetric> metrics = requests.stream()
                                    .map(dto -> dto.toEntity(family))
                                    .toList();
                    
                    healthMetricPersistenceService.saveAllWithNewTransaction(metrics);
                } catch (Exception e) {
                    log.error("Failed to save health metrics to DB (Schema/Constraint Issue). Skipping save.", e);
                }
        }

        public org.ssafy.eeum.domain.health.entity.HealthMetric getPatientLatestMetrics(Integer groupId) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

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
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                org.ssafy.eeum.domain.family.entity.Supporter patient = supporterRepository
                                .findByFamilyAndRole(family, org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "그룹 내 피부양자를 찾을 수 없습니다."));

                org.ssafy.eeum.domain.auth.entity.User user = patient.getUser();
                if (user == null || user.getFcmToken() == null) {
                        throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "피부양자의 기기 정보(토큰)가 없습니다.");
                }

                // Send High Priority FCM to Trigger Measurement
                // title, body, type, notificationId, route, familyId, groupName, eventId
                try {
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
                } catch (FcmUnregisteredTokenException e) {
                    log.warn("FCM Token is invalid/unregistered. Removing token for user: {}", user.getId());
                    user.updateFcmToken(null);
                }
                
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
            Family family = familyRepository.findById(groupId)
                            .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                            "가족 그룹을 찾을 수 없습니다."));

            org.ssafy.eeum.domain.family.entity.Supporter patient = supporterRepository
                            .findByFamilyAndRole(family, org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                            .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                            "그룹 내 피부양자를 찾을 수 없습니다."));

            org.ssafy.eeum.domain.auth.entity.User user = patient.getUser();
            if (user == null || user.getFcmToken() == null) {
                    throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "피부양자의 기기 정보(토큰)가 없습니다.");
            }

            try {
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
            } catch (FcmUnregisteredTokenException e) {
                log.warn("FCM Token is invalid/unregistered. Removing token for user: {}", user.getId());
                user.updateFcmToken(null);
            }
            
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
                         return false; 
                     }
                     
                     boolean abnormal = hr.getAvgRate() < 50 || hr.getAvgRate() > 120;
                     return abnormal;
                })
                .orElse(false);
    }
}
