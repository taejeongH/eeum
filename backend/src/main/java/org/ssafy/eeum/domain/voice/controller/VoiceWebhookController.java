package org.ssafy.eeum.domain.voice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.entity.VoiceTask;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.domain.voice.service.VoiceService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/voice/webhook")
@RequiredArgsConstructor
public class VoiceWebhookController {

    private final MessageRepository messageRepository;
    private final VoiceLogRepository voiceLogRepository;
    private final IotSyncService iotSyncService;
    private final VoiceService voiceService;

    @PostMapping("/tts")
    public void handleTtsWebhook(@RequestParam Integer messageId, @RequestBody Map<String, Object> payload) {
        log.info("[TTS 웹후크] 콜백 수신 - messageId: {}, payload: {}", messageId, payload);

        String status = (String) payload.get("status");
        if ("COMPLETED".equals(status)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) payload.get("output");
            if (output != null && "success".equals(output.get("status"))) {
                String voiceUrl = (String) output.get("url");
                processTtsResult(messageId, voiceUrl);
            } else {
                log.error("[TTS 웹후크] 작업은 완료되었으나 실패 응답 수신: {}", output);
                updateTaskStatus(messageId, VoiceTask.TaskStatus.FAILED);
            }
        } else if ("FAILED".equals(status)) {
            log.error("[TTS 웹후크] 작업 실패 수신: {}", payload);
            updateTaskStatus(messageId, VoiceTask.TaskStatus.FAILED);
        }
    }

    private void updateTaskStatus(Integer messageId, VoiceTask.TaskStatus status) {
        messageRepository.findById(messageId).ifPresent(message -> {
            if (message.getVoiceTask() != null) {
                message.getVoiceTask().updateStatus(status);
                messageRepository.save(message);
            }
        });
    }

    @PostMapping("/test")
    public void handleTestWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[TTS 테스트 웹후크] 콜백 수신: {}", payload);
        String status = (String) payload.get("status");
        if ("COMPLETED".equals(status)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) payload.get("output");
            if (output != null && "success".equals(output.get("status"))) {
                log.info("[TTS 테스트 웹후크] 테스트 성공! URL: {}", output.get("url"));
            }
        }
    }

    private void processTtsResult(Integer messageId, String voiceUrl) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message != null) {
            // S3 키 추출 및 메시지의 voiceUrl 업데이트
            String voiceKey = voiceService.extractS3Key(voiceUrl);
            message.updateVoiceUrl(voiceKey);

            // 연관된 VoiceTask가 있다면 완료 처리
            if (message.getVoiceTask() != null) {
                message.getVoiceTask().updateResult(voiceKey);
            }

            messageRepository.save(message);

            // 음성 메시지 추가 로그 기록
            VoiceLog voiceLog = VoiceLog.builder()
                    .groupId(message.getGroup().getId())
                    .voiceId(messageId)
                    .actionType(ActionType.ADD)
                    .build();
            voiceLogRepository.save(voiceLog);

            // IoT 기기에 동기화 알림 발송
            iotSyncService.notifyUpdate(message.getGroup().getId(), "voice");
            log.info("[TTS 웹후크] 메시지 처리 및 알림 발송 완료. ID: {}", messageId);
        } else {
            log.error("[TTS 웹후크] 해당 ID의 메시지를 찾을 수 없습니다: {}", messageId);
        }
    }
}
