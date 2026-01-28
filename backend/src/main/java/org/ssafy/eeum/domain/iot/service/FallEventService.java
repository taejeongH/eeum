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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FallEventService {

    private final FallEventRepository fallEventRepository;
    private final FamilyRepository familyRepository;
    private final S3Service s3Service;
    private final GmsService gmsService;

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
