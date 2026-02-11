package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.iot.entity.FallEvent;
import org.ssafy.eeum.domain.iot.repository.FallEventRepository;
import org.ssafy.eeum.global.infra.s3.S3Service;
import org.ssafy.eeum.global.infra.gms.GmsService;
import org.ssafy.eeum.domain.health.service.HealthService;
import org.ssafy.eeum.domain.notification.service.FallDetectionService;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.domain.iot.dto.FallDetectionRequestDTO;
import org.ssafy.eeum.domain.iot.dto.FallEventHistoryResponseDTO;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 낙상 이벤트의 감지, 처리, 영상 관리 및 후속 조치를 담당하는 서비스 클래스입니다.
 * 기기로부터의 센서 데이터를 분석하고, 필요시 보호자 알림 및 심박수 측정을 연동합니다.
 * 
 * @summary 낙상 이벤트 관리 및 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FallEventService {

    private final FallEventRepository fallEventRepository;
    private final FamilyRepository familyRepository;
    private final IotDeviceRepository iotDeviceRepository;
    private final S3Service s3Service;
    private final GmsService gmsService;
    private final FallDetectionService fallDetectionService;
    private final HealthService healthService;

    /**
     * IoT 기기로부터 수신된 낙상 감지 데이터를 처리합니다.
     * 위험도가 높은 경우(1레벨) 영상 업로드 절차를 시작하고 심박수 측정을 요청합니다.
     * 
     * @summary 낙상 감지 데이터 처리
     * @param serialNumber 기기 시리얼 번호
     * @param request      낙상 감지 요청 정보
     * @param groupId      가족 그룹 식별자
     * @return 영상 업로드 및 이벤트 식별 정보
     */
    @Transactional
    public Map<String, String> handleFallDetection(String serialNumber, FallDetectionRequestDTO request,
            Integer groupId) {
        IotDevice device = getValidatedDevice(serialNumber, groupId);
        Family family = device.getFamily();
        Integer riskLevel = request.getData().getLevel();

        FallEvent event = createInitialFallEvent(family, request);

        Map<String, String> response = new HashMap<>();
        if (isHighRisk(riskLevel)) {
            processHighRiskFall(family.getId(), event, response);
        }

        fallEventRepository.save(event);
        return response;
    }

    private IotDevice getValidatedDevice(String serialNumber, Integer groupId) {
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "등록되지 않은 기기입니다."));

        if (device.getFamily().getId().equals(groupId)) {
            return device;
        }
        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS, "해당 기기에 대한 접근 권한이 없습니다.");
    }

    private FallEvent createInitialFallEvent(Family family, FallDetectionRequestDTO request) {
        return FallEvent.builder()
                .family(family)
                .severity(request.getData().getLevel())
                .confidence(request.getData().getConfidence())
                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                .videoStatus(FallEvent.VideoStatus.NONE)
                .build();
    }

    private boolean isHighRisk(Integer riskLevel) {
        return Integer.valueOf(1).equals(riskLevel);
    }

    private void processHighRiskFall(Integer familyId, FallEvent event, Map<String, String> response) {
        String videoFileName = "fall/" + familyId + "/" + UUID.randomUUID() + ".mp4";
        String presignedUrl = s3Service.generatePresignedUrl(videoFileName, "video/mp4");

        event.setVideoPath(videoFileName);
        event.updateVideoStatus(FallEvent.VideoStatus.PENDING);

        log.info("[동작] 워치 심박수 측정 요청 전송 - 가족ID: {}", familyId);
        healthService.requestMeasurement(familyId);

        response.put("presignedUrl", presignedUrl);
        response.put("videoPath", videoFileName);
        response.put("eventId", String.valueOf(event.getId()));
    }

    /**
     * 수동으로 낙상 로그 생성을 시작하고 영상 업로드를 준비합니다.
     * 
     * @summary 낙상 로그 초기화 및 URL 발급
     * @param familyId 가족 그룹 식별자
     * @return 영상 업로드 및 이벤트 식별 정보
     */
    @Transactional
    public Map<String, String> initiateFallLog(Integer familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "가족 그룹을 찾을 수 없습니다."));

        String videoFileName = "fall/" + familyId + "/" + UUID.randomUUID() + ".mp4";
        FallEvent event = createManualFallEvent(family, videoFileName);
        fallEventRepository.save(event);

        healthService.requestMeasurement(familyId);
        return createUploadResponse(event, videoFileName);
    }

    private FallEvent createManualFallEvent(Family family, String videoFileName) {
        return FallEvent.builder()
                .family(family)
                .severity(1)
                .videoPath(videoFileName)
                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                .videoStatus(FallEvent.VideoStatus.PENDING)
                .build();
    }

    private Map<String, String> createUploadResponse(FallEvent event, String videoFileName) {
        String presignedUrl = s3Service.generatePresignedUrl(videoFileName, "video/mp4");
        Map<String, String> response = new HashMap<>();
        response.put("presignedUrl", presignedUrl);
        response.put("videoPath", videoFileName);
        response.put("eventId", String.valueOf(event.getId()));
        return response;
    }

    /**
     * 낙상 이벤트 정보를 저장합니다.
     * 
     * @summary 낙상 이벤트 엔티티 저장
     * @param fallEvent 저장할 낙상 이벤트 엔티티
     * @return 저장된 엔티티
     */
    @Transactional
    public FallEvent saveFallEvent(FallEvent fallEvent) {
        return fallEventRepository.save(fallEvent);
    }

    /**
     * 영상 파일 업로드가 완료되었음을 기록합니다.
     * 
     * @summary 영상 업로드 상태 완료로 변경
     * @param videoPath 업로드된 S3 파일 경로
     */
    @Transactional
    public void completeFallLog(String videoPath) {
        FallEvent event = fallEventRepository.findByVideoPath(videoPath)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND));

        event.updateVideoStatus(FallEvent.VideoStatus.SUCCESS);
    }

    /**
     * 음성 인식 결과(STT)를 분석하여 위급 상황 여부를 최종 판단합니다.
     * 감성 분석 결과와 실시간 심박수 데이터를 교차 검증합니다.
     * 
     * @summary 음성 응답 기반 위급 상황 분석
     * @param familyId        가족 그룹 식별자
     * @param voiceTranscript 인식된 음성 텍스트 내용
     */
    @Transactional
    public void handleVoiceResponse(Integer familyId, String voiceTranscript) {
        FallEvent event = getLastUnderReviewEvent(familyId);

        boolean isEmergencyByAi = gmsService.analyzeSentiment(voiceTranscript);
        boolean isEmergencyByKeyword = containsEmergencyKeyword(voiceTranscript);
        boolean isEmergencyByHeartRate = healthService.isHeartRateAbnormal(familyId);

        log.info("[검증] 이중 검증 - 가족ID: {}, 음성: '{}', AI분석: {}, 키워드감지: {}, 심박수이상: {}",
                familyId, voiceTranscript, isEmergencyByAi, isEmergencyByKeyword, isEmergencyByHeartRate);

        if (isEmergencyByAi || isEmergencyByKeyword || isEmergencyByHeartRate) {
            processEmergencySituation(familyId, event, voiceTranscript, isEmergencyByHeartRate);
        } else {
            processSafeSituation(familyId, event, voiceTranscript);
        }
    }

    private FallEvent getLastUnderReviewEvent(Integer familyId) {
        return fallEventRepository.findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(
                familyId, FallEvent.StatusType.UNDER_REVIEW)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private boolean containsEmergencyKeyword(String text) {
        return text.contains("EMERGENCY") || text.contains("비상") || text.contains("도와줘") || text.contains("살려");
    }

    private void processEmergencySituation(Integer familyId, FallEvent event, String transcript,
            boolean isHeartRateProblem) {
        String emergencyReason = transcript;
        if (isHeartRateProblem) {
            emergencyReason += " (심박수 이상 감지됨)";
        }

        event.updateToEmergency(emergencyReason);
        log.warn("[결과] 🚨 위급 상황 확정 - 가족ID: {}, 사유: {}", familyId, emergencyReason);

        fallDetectionService.handleFallDetection(familyId, "낙상 위험 상황이 감지되었습니다: " + emergencyReason, event.getId());
    }

    private void processSafeSituation(Integer familyId, FallEvent event, String transcript) {
        event.updateToSafe(transcript);
        log.info("[결과] ✅ 안전 상황 확정 - 가족ID: {}, 내용: '{}'", familyId, transcript);
    }

    /**
     * 감성 분석 로직을 테스트합니다.
     * 
     * @summary 감성 분석 테스트
     * @param familyId        가족 그룹 식별자
     * @param voiceTranscript 테스트할 텍스트
     */
    public void testSentimentAnalysis(Integer familyId, String voiceTranscript) {
        log.info("AI 분석 테스트 시작 - 내용: {}, 가족ID: {}", voiceTranscript, familyId);
        boolean isEmergency = gmsService.analyzeSentiment(voiceTranscript);
        log.info("AI 분석 테스트 결과 - 위급여부: {}", isEmergency);

        if (isEmergency) {
            log.info("테스트 알림 발송 - 가족ID: {}", familyId);
            fallDetectionService.handleFallDetection(familyId, "[테스트] 낙상 위험 감지: " + voiceTranscript, null);
        }
    }

    /**
     * 낙상 감지 후 일정 시간 동안 음성 응답이 없는 경우를 처리합니다.
     * 
     * @summary 음성 응답 누락 처리(자동 비상 전환)
     * @param familyId 가족 그룹 식별자
     */
    @Transactional
    public void handleEmptyVoiceResponse(Integer familyId) {
        FallEvent event = fallEventRepository.findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(
                familyId, FallEvent.StatusType.UNDER_REVIEW)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND, "분석 중인 낙상 이벤트를 찾을 수 없습니다."));

        event.updateToEmergency("응답 없음 (STT 실패)");
        fallDetectionService.handleFallDetection(familyId, "낙상 감지 후 응답이 없어 비상 상황으로 전환되었습니다.", event.getId());
    }

    /**
     * 분석 대기 중인 낙상 이벤트 중 시간이 초과된 건들을 주기적으로 체크하여 비상 상황으로 전환합니다.
     * 
     * @summary 타임아웃 이벤트 관리 스케줄러
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void checkTimeoutFallEvents() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(1);

        List<FallEvent> timedOutEvents = fallEventRepository.findByStatusTypeAndCreatedAtBefore(
                FallEvent.StatusType.UNDER_REVIEW,
                timeout);

        for (FallEvent event : timedOutEvents) {
            event.updateToEmergency("응답 없음 (타임아웃)");
            fallDetectionService.handleFallDetection(event.getFamily().getId(),
                    "낙상 확인 요청에 1분간 응답이 없어 비상 상황으로 전환되었습니다.", event.getId());
        }
    }

    /**
     * 특정 낙상 이벤트의 녹화 영상 S3 Presigned URL을 생성합니다.
     * 
     * @summary 낙상 영상 URL 조회
     * @param eventId 낙상 이벤트 식별자
     * @return 영상 URL 정보를 포함한 맵
     */
    public Map<String, String> getVideoUrl(Integer eventId) {
        FallEvent event = fallEventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "낙상 이벤트를 찾을 수 없습니다."));

        if (event.getVideoStatus() != FallEvent.VideoStatus.SUCCESS) {
            throw new CustomException(ErrorCode.VIDEO_NOT_READY);
        }

        String presignedUrl = s3Service.getPresignedUrl(event.getVideoPath());

        Map<String, String> response = new HashMap<>();
        response.put("videoUrl", presignedUrl);

        return response;
    }

    /**
     * 특정 가족 그룹의 전체 낙상 이력을 조회합니다.
     * 각 이력에 대해 유효한 영상 URL이 있을 경우 함께 반환합니다.
     * 
     * @summary 가족별 낙상 히스토리 조회
     * @param familyId 가족 그룹 식별자
     * @return 낙상 이력 응답 DTO 리스트
     */
    public List<FallEventHistoryResponseDTO> getFallHistory(Integer familyId) {
        List<FallEvent> events = fallEventRepository.findByFamilyIdOrderByCreatedAtDesc(familyId);

        return events.stream()
                .map(event -> {
                    String videoUrl = null;
                    if (event.getVideoStatus() == FallEvent.VideoStatus.SUCCESS && event.getVideoPath() != null) {
                        videoUrl = s3Service.getPresignedUrl(event.getVideoPath());
                    }
                    return FallEventHistoryResponseDTO.of(event, videoUrl);
                })
                .collect(Collectors.toList());
    }
}
