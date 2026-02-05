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

    @Transactional
    public Map<String, String> handleFallDetection(String serialNumber, FallDetectionRequestDTO request,
            Integer groupId) {
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "등록되지 않은 기기입니다."));

        if (!device.getFamily().getId().equals(groupId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS, "해당 기기에 대한 접근 권한이 없습니다.");
        }

        Family family = device.getFamily();
        Integer level = request.getData().getLevel();

        FallEvent.FallEventBuilder eventBuilder = FallEvent.builder()
                .family(family)
                .severity(level)
                .confidence(request.getData().getConfidence())
                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                .videoStatus(FallEvent.VideoStatus.NONE);

        String fileName = null;
        String presignedUrl = null;

        if (Integer.valueOf(1).equals(level)) {
            fileName = "fall/" + family.getId() + "/" + UUID.randomUUID() + ".mp4";
            eventBuilder.videoPath(fileName);
            eventBuilder.videoStatus(FallEvent.VideoStatus.PENDING);
            presignedUrl = s3Service.generatePresignedUrl(fileName, "video/mp4");

            // [NEW] Trigger Heart Rate Measurement automatically
            log.info("Fall Detected (Level 1). Triggering Heart Rate Measurement for Group: {}", groupId);
            healthService.requestMeasurement(groupId);
        }

        FallEvent event = eventBuilder
                .videoStatus(
                        Integer.valueOf(1).equals(level) ? FallEvent.VideoStatus.PENDING : FallEvent.VideoStatus.NONE)
                .build();
        fallEventRepository.save(event);

        Map<String, String> response = new HashMap<>();
        if (presignedUrl != null) {
            response.put("presignedUrl", presignedUrl);
            response.put("videoPath", fileName);
            response.put("eventId", String.valueOf(event.getId()));
        }

        log.info("Fall Detected: Device={}, Level={}, VideoUploadExpected={}",
                serialNumber, level, presignedUrl != null);

        return response;
    }

    @Transactional
    public java.util.Map<String, String> initiateFallLog(Integer familyId) {
        String fileName = "fall/" + familyId + "/" + UUID.randomUUID() + ".mp4";

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "가족 그룹을 찾을 수 없습니다."));

        FallEvent event = FallEvent.builder()
                .family(family)
                .severity(1)
                .videoPath(fileName)
                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                .videoStatus(FallEvent.VideoStatus.PENDING)
                .build();
        fallEventRepository.save(event);

        String url = s3Service.generatePresignedUrl(fileName, "video/mp4");
        healthService.requestMeasurement(familyId);

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("presignedUrl", url);
        response.put("videoPath", fileName);
        response.put("eventId", String.valueOf(event.getId()));

        return response;
    }

    @Transactional
    public FallEvent saveFallEvent(FallEvent fallEvent) {
        return fallEventRepository.save(fallEvent);
    }

    @Transactional
    public void completeFallLog(String videoPath) {
        FallEvent event = fallEventRepository.findByVideoPath(videoPath)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND));

        event.updateVideoStatus(FallEvent.VideoStatus.SUCCESS);
    }

    @Transactional
    public void handleVoiceResponse(Integer familyId, String sttContent) {
        // 해당 그룹에서 가장 최근에 발생한 분석 중인 이벤트를 찾음
        FallEvent event = fallEventRepository.findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(
                familyId, FallEvent.StatusType.UNDER_REVIEW)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND));

        log.info("[DEBUG_FALL] Handling Voice Response for Family: {}, STT: {}", familyId, sttContent);

        boolean gmsAnalysisResult = gmsService.analyzeSentiment(sttContent);
        log.info("[DEBUG_FALL] GMS Analysis Result: {}", gmsAnalysisResult);

        boolean isSentimentEmergency = sttContent.contains("EMERGENCY") || sttContent.contains("비상")
                || gmsAnalysisResult;
        boolean isHeartRateEmergency = healthService.isHeartRateAbnormal(familyId);

        log.info("[DEBUG_FALL] Dual Verification - Group: {}, STT: '{}', Sentiment: {} (GMS: {}), HR: {}",
                familyId, sttContent, isSentimentEmergency, gmsAnalysisResult, isHeartRateEmergency);

        if (isSentimentEmergency || isHeartRateEmergency) {
            String emergencyReason = sttContent;
            if (isHeartRateEmergency) {
                emergencyReason += " (심박수 이상 감지됨)";
            }

            event.updateToEmergency(emergencyReason);
            log.warn("[DEBUG_FALL] 낙상 위급 상황 판단 (EMERGENCY) - Group: {}, Reason: {}, Final Status: EMERGENCY", familyId,
                    emergencyReason);

            fallDetectionService.handleFallDetection(familyId, "낙상 위험 상황이 감지되었습니다: " + emergencyReason, event.getId());
        } else {
            event.updateToSafe(sttContent);
            log.info("[DEBUG_FALL] 낙상 안전 확인 (SAFE) - Group: {}, Text: '{}', Final Status: SAFE", familyId, sttContent);
        }
    }

    public void testSentimentAnalysis(Integer familyId, String sttContent) {
        log.info("LLM Test Start - Content: {}, FamilyId: {}", sttContent, familyId);
        boolean isEmergency = gmsService.analyzeSentiment(sttContent);
        log.info("LLM Test Results - Is Emergency: {}", isEmergency);

        if (isEmergency) {
            log.info("Triggering Test Notification for Family: {}", familyId);
            fallDetectionService.handleFallDetection(familyId, "[테스트] 낙상 위험 감지: " + sttContent, null);
        }
    }

    @Transactional
    public void handleEmptyVoiceResponse(Integer familyId) {
        FallEvent event = fallEventRepository.findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(
                familyId, FallEvent.StatusType.UNDER_REVIEW)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND, "분석 중인 낙상 이벤트를 찾을 수 없습니다."));

        event.updateToEmergency("응답 없음 (STT 실패)");
        fallDetectionService.handleFallDetection(familyId, "낙상 감지 후 응답이 없어 비상 상황으로 전환되었습니다.", event.getId());
    }

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

    public java.util.Map<String, String> getVideoUrl(Integer eventId) {
        FallEvent event = fallEventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "낙상 이벤트를 찾을 수 없습니다."));

        if (event.getVideoStatus() != FallEvent.VideoStatus.SUCCESS) {
            throw new CustomException(ErrorCode.VIDEO_NOT_READY);
        }

        String presignedUrl = s3Service.getPresignedUrl(event.getVideoPath());

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("videoUrl", presignedUrl);

        return response;
    }

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
