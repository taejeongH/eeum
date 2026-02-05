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

        String status = (String) payload.get("status");
        if ("COMPLETED".equals(status)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) payload.get("output");
            if (output != null && "success".equals(output.get("status"))) {
                String voiceUrl = (String) output.get("url");
                processTtsResult(messageId, voiceUrl);
            }
        }
    }

    @PostMapping("/test")
    public void handleTestWebhook(@RequestBody Map<String, Object> payload) {
        String status = (String) payload.get("status");
        if ("COMPLETED".equals(status)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) payload.get("output");
            if (output != null && "success".equals(output.get("status"))) {
                log.info("[RunPod Webhook) 테스트 성공! URL: {}", output.get("url"));
            }
        }
    }

    private void processTtsResult(Integer messageId, String voiceUrl) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message != null) {
            String voiceKey = voiceService.extractS3Key(voiceUrl);
            message.updateVoiceUrl(voiceKey);
            messageRepository.save(message);

            VoiceLog voiceLog = VoiceLog.builder()
                    .groupId(message.getGroup().getId())
                    .voiceId(messageId)
                    .actionType(ActionType.ADD)
                    .build();
            voiceLogRepository.save(voiceLog);

            iotSyncService.notifyUpdate(message.getGroup().getId(), "voice");
        } else {

        }
    }
}
