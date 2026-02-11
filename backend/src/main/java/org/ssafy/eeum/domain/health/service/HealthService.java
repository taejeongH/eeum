package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.domain.health.dto.HealthMetricRequestDTO;
import org.ssafy.eeum.domain.health.dto.HeartRateRequestDTO;
import org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.health.entity.HeartRate;
import org.ssafy.eeum.domain.health.repository.HealthMetricRepository;
import org.ssafy.eeum.domain.health.repository.HeartRateRepository;
import org.ssafy.eeum.domain.iot.entity.FallEvent;
import org.ssafy.eeum.domain.iot.repository.FallEventRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.fcm.FcmService;
import org.ssafy.eeum.global.infra.fcm.FcmUnregisteredTokenException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 및 피부양자의 건강 지표 데이터를 관리하고 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 건강 지표 저장, 조회, 심박수 측정 요청 및 이상 징후 판단 기능을 제공합니다.
 * 
 * @summary 건강 데이터 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class HealthService {

    private final HealthMetricRepository healthMetricRepository;
    private final FamilyRepository familyRepository;
    private final SupporterRepository supporterRepository;
    private final FcmService fcmService;
    private final HealthMetricPersistenceService healthMetricPersistenceService;
    private final HeartRateRepository heartRateRepository;
    private final FallEventRepository fallEventRepository;

    /**
     * 수집된 건강 지표 데이터들을 데이터베이스에 저장합니다.
     * 별도의 트랜잭션 서비스를 통해 개별 저장 실패가 전체 로직에 영향을 주지 않도록 처리합니다.
     * 
     * @summary 건강 지표 데이터 저장
     * @param groupId  가족 그룹 식별자
     * @param requests 저장할 건강 지표 요청 DTO 목록
     */
    @Transactional
    public void saveHealthMetrics(Integer groupId, List<HealthMetricRequestDTO> requests) {
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "가족 그룹을 찾을 수 없습니다."));

        try {
            List<HealthMetric> metrics = requests.stream()
                    .map(dto -> dto.toEntity(family))
                    .toList();

            healthMetricPersistenceService.saveAllWithNewTransaction(metrics);
        } catch (Exception e) {
            log.error("Failed to save health metrics to DB (Schema/Constraint Issue). Skipping save.", e);
        }
    }

    /**
     * 가족 그룹 내 피부양자의 가장 최신 건강 지표 정보를 조회합니다.
     * 
     * @summary 피부양자 최신 건강 지표 조회
     * @param groupId 가족 그룹 식별자
     * @return 최신 건강 지표 데이터 (없을 시 null)
     */
    public HealthMetric getPatientLatestMetrics(Integer groupId) {
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "가족 그룹을 찾을 수 없습니다."));

        supporterRepository
                .findByFamilyAndRole(family, Supporter.Role.PATIENT)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "그룹 내 피부양자를 찾을 수 없습니다."));

        return healthMetricRepository.findFirstByFamilyOrderByRecordDateDesc(family)
                .orElse(null);
    }

    /**
     * 피부양자의 웨어러블 기기에 실시간 심박수 측정을 요청하는 FCM 메시지를 전송합니다.
     * 
     * @summary 실시간 심박수 측정 요청 전송
     * @param groupId 가족 그룹 식별자
     */
    @Transactional
    public void requestMeasurement(Integer groupId) {
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "가족 그룹을 찾을 수 없습니다."));

        Supporter patient = supporterRepository
                .findByFamilyAndRole(family, Supporter.Role.PATIENT)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "그룹 내 피부양자를 찾을 수 없습니다."));

        User user = patient.getUser();
        if (user == null || user.getFcmToken() == null) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "피부양자의 기기 정보(토큰)가 없습니다.");
        }

        try {
            fcmService.sendMessageTo(
                    user.getFcmToken(),
                    null,
                    null,
                    "CMD_MEASURE_HR",
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

    /**
     * 특정 가족 그룹의 최신 심박수 데이터를 조회합니다.
     * 
     * @summary 최신 심박수 정보 조회
     * @param groupId 가족 그룹 식별자
     * @return 심박수 정보 DTO (없을 시 null)
     */
    public HeartRateResponseDTO getLatestHeartRate(Integer groupId) {
        return heartRateRepository.findLatestByFamilyId(groupId)
                .orElse(null);
    }

    /**
     * 특정 낙상 이벤트 발생 시점에 측정된 심박수 집계 정보를 조회합니다.
     * 
     * @summary 이벤트 관련 심박수 결과 조회
     * @param eventId 낙상 이벤트 식별자
     * @return 집계된 심박수 정보 DTO
     */
    public HeartRateResponseDTO getHeartRateResult(Integer eventId) {
        fallEventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        return heartRateRepository.findAggregatedMetricsByFallEventId(eventId);
    }

    /**
     * 피부양자의 모바일 기기에 누적된 건강 데이터를 동기화하도록 요청하는 FCM 메시지를 전송합니다.
     * 
     * @summary 건강 데이터 동기화 요청 전송
     * @param groupId 가족 그룹 식별자
     */
    public void requestHealthSync(Integer groupId) {
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "가족 그룹을 찾을 수 없습니다."));

        Supporter patient = supporterRepository
                .findByFamilyAndRole(family, Supporter.Role.PATIENT)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "그룹 내 피부양자를 찾을 수 없습니다."));

        User user = patient.getUser();
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

    /**
     * 워치 등 기기에서 측정 완료된 심박수 데이터를 저장합니다.
     * 
     * @summary 심박수 데이터 저장
     * @param request 심박수 저장 요청 DTO
     */
    @Transactional
    public void saveHeartRate(HeartRateRequestDTO request) {
        Family family = familyRepository.findById(request.getFamilyId())
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "가족 그룹을 찾을 수 없습니다."));

        FallEvent fallEvent = null;
        if (request.getRelatedId() != null) {
            fallEvent = fallEventRepository.findById(request.getRelatedId())
                    .orElse(null);
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
     * 특정 가족 그룹 피부양자의 최신 심박수가 정상 범위를 벗어났는지 확인합니다.
     * 2분 이내의 데이터가 없으면 정상으로 간주합니다.
     * 
     * @summary 심박수 이상 여부 판단
     * @param groupId 가족 그룹 식별자
     * @return 이상 여부 (true: 비정상, false: 정상)
     */
    public boolean isHeartRateAbnormal(Integer groupId) {
        return heartRateRepository.findFirstByFamilyIdOrderByMeasuredAtDesc(groupId)
                .map(hr -> {
                    if (hr.getMeasuredAt().isBefore(LocalDateTime.now().minusMinutes(2))) {
                        return false;
                    }

                    return hr.getAvgRate() < 50 || hr.getAvgRate() > 120;
                })
                .orElse(false);
    }
}
