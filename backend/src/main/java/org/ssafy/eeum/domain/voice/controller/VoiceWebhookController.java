package org.ssafy.eeum.domain.voice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/voice/webhook")
@RequiredArgsConstructor
public class VoiceWebhookController {

    private final MessageRepository messageRepository;
    private final VoiceLogRepository voiceLogRepository;
    private final IotSyncService iotSyncService;
    private final org.ssafy.eeum.domain.voice.service.VoiceService voiceService;

    @PostMapping("/tts")
    public void handleTtsWebhook(@RequestParam Integer messageId, @RequestBody Map<String, Object> payload) {
        log.info("[RunPod Webhook] Received status for messageId: {}. Payload: {}", messageId, payload);

        String status = (String) payload.get("status");
        if ("COMPLETED".equals(status)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) payload.get("output");
            if (output != null && "success".equals(output.get("status"))) {
                String voiceUrl = (String) output.get("url");
                processTtsResult(messageId, voiceUrl);
            }
        } else {
            log.warn("[RunPod Webhook] Job failed or returned non-completed status: {}", status);
        }
    }

    private void processTtsResult(Integer messageId, String voiceUrl) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message != null) {
            String voiceKey = voiceService.extractS3Key(voiceUrl);
            log.info("[RunPod Webhook] Updating messageId {} with voiceKey: {}", messageId, voiceKey);
            message.updateVoiceUrl(voiceKey);
            messageRepository.save(message);

            // 1. 로그 저장
            VoiceLog voiceLog = VoiceLog.builder()
                    .groupId(message.getGroup().getId())
                    .voiceId(messageId)
                    .actionType(ActionType.ADD)
                    .build();
            voiceLogRepository.save(voiceLog);

            // 2. IoT 동기화 알림 (5개 쌓일 때까지 혹은 1시간 후에 배치 전송)
            iotSyncService.notifyUpdate(message.getGroup().getId(), "voice");
        } else {
            log.error("[RunPod Webhook] Message not found: id={}", messageId);
        }
    }
}
