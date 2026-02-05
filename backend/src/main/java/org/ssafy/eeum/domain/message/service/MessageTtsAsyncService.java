package org.ssafy.eeum.domain.message.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.domain.voice.service.VoiceService;

import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTtsAsyncService {

    private final VoiceService voiceService;
    private final MessageRepository messageRepository;
    private final VoiceLogRepository voiceLogRepository;
    private final IotSyncService iotSyncService;
    private final org.ssafy.eeum.domain.voice.repository.VoiceTaskRepository taskRepository;

    @Async
    @Transactional
    public void processTtsAsync(Integer messageId, Integer userId, String content, Integer groupId) {
        String voiceUrl = null;

        try {
            voiceUrl = voiceService.createTtsUrl(userId, content, messageId);
        } catch (Exception e) {
            log.error("[TTS Async] TTS creation request failed: {}", e.getMessage());
            return;
        }

        try {
            // Case 1: Deferred processing (Task ID returned)
            if (voiceUrl != null && voiceUrl.startsWith("TASK_ID:")) {
                Integer taskId = Integer.parseInt(voiceUrl.substring(8));
                log.info("[TTS Async] messageId: {} TTS generation delayed. Waiting (Task ID: {}).", messageId, taskId);

                taskRepository.findById(taskId).ifPresent(task -> {
                    messageRepository.findById(messageId).ifPresent(msg -> {
                        msg.updateVoiceTask(task);
                        messageRepository.save(msg);
                    });
                });
                // REMOVED: return; -> Proceed to Log Creation (VoiceUrl will be null here,
                // effectively Text-Only for now)
                voiceUrl = null;
            }

            // Case 2: Immediate completion or Text-only fallback
            Message message = messageRepository.findById(messageId).orElse(null);
            if (message != null) {
                // If we have a voice URL (success), update the message
                if (voiceUrl != null) {
                    String voiceKey = voiceService.extractS3Key(voiceUrl);
                    message.updateVoiceUrl(voiceKey);
                    messageRepository.save(message);
                }

                // Create VoiceLog (Triggers IoT Sync for both Voice and Text messages)
                VoiceLog voiceLog = VoiceLog.builder()
                        .groupId(groupId)
                        .voiceId(messageId)
                        .actionType(ActionType.ADD)
                        .build();
                voiceLogRepository.save(voiceLog);

                // Notify IoT
                iotSyncService.notifyUpdate(groupId, "voice");

                log.info("[TTS Async] Message processing completed. ID: {}, HasVoice: {}", messageId,
                        (voiceUrl != null));
            } else {
                log.error("[TTS Async] Message not found during post-processing: id={}", messageId);
            }
        } catch (Exception e) {
            log.error("[TTS Async] Error during message post-processing (messageId: {}): {}", messageId,
                    e.getMessage());
        }
    }
}
