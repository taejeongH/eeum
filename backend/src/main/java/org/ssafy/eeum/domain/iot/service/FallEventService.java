package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.iot.entity.FallEvent;
import org.ssafy.eeum.domain.iot.repository.FallEventRepository;
import org.ssafy.eeum.global.infra.s3.S3Service;
import org.ssafy.eeum.global.infra.gms.GmsService;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.domain.iot.dto.FallDetectionRequestDTO;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

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

    @Transactional
    public Map<String, String> handleFallDetection(String serialNumber, FallDetectionRequestDTO request) {
        // 1. 기기 조회를 통해 가족 그룹 식별
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "등록되지 않은 기기입니다."));

        Family family = device.getFamily();
        Integer level = request.getData().getLevel();

        // 2. 낙상 이벤트 생성 및 저장
        FallEvent.FallEventBuilder eventBuilder = FallEvent.builder()
                .family(family)
                .severity(level)
                .statusType(FallEvent.StatusType.UNDER_REVIEW);

        String fileName = null;
        String presignedUrl = null;

        // 3. 레벨 1인 경우에만 비디오 업로드 경로 및 Presigned URL 생성
        if (Integer.valueOf(1).equals(level)) {
            fileName = "fall/" + family.getId() + "/" + UUID.randomUUID() + ".mp4";
            eventBuilder.videoPath(fileName);
            presignedUrl = s3Service.generatePresignedUrl(fileName, "video/mp4");
        }

        FallEvent event = eventBuilder.build();
        fallEventRepository.save(event);

        // 4. 응답 구성
        Map<String, String> response = new HashMap<>();
        if (presignedUrl != null) {
            response.put("presignedUrl", presignedUrl);
            response.put("videoPath", fileName);
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
                .build();

        fallEventRepository.save(event);

        String url = s3Service.generatePresignedUrl(fileName, "video/mp4");

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("presignedUrl", url);
        response.put("videoPath", fileName);

        return response;
    }

    @Transactional
    public void completeFallLog(String videoPath) {
        FallEvent event = fallEventRepository.findByVideoPath(videoPath)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND));

        log.info("Fall Event Video Upload Complete: {}", videoPath);
    }

    @Transactional
    public void handleVoiceResponse(Integer familyId, String sttContent) {
        // 해당 그룹에서 가장 최근에 발생한 '분석 중'인 이벤트를 찾음
        FallEvent event = fallEventRepository.findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(
                familyId, FallEvent.StatusType.UNDER_REVIEW)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.ENTITY_NOT_FOUND));

        boolean isEmergency = gmsService.analyzeSentiment(sttContent);

        if (isEmergency) {
            event.updateToEmergency(sttContent);
            log.warn("낙상 위급 상황 판단 (LLM): Group={}, Text={}", familyId, sttContent);
        } else {
            event.updateToSafe(sttContent);
            log.info("낙상 안전 확인 (LLM): Group={}, Text={}", familyId, sttContent);
        }
    }
}
