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

        try {
            String voiceUrl = voiceService.createTtsUrl(userId, content, messageId);

            if (voiceUrl != null) {
                // 즉시 완료되지 않은 경우
                if (voiceUrl.startsWith("TASK_ID:")) {
                    Integer taskId = Integer.parseInt(voiceUrl.substring(8));
                    log.info("[TTS Async] messageId: {} 에 대한 TTS 생성이 지연되어 대기 상태(Task ID: {})로 전환합니다.", messageId,
                            taskId);

                    taskRepository.findById(taskId).ifPresent(task -> {
                        messageRepository.findById(messageId).ifPresent(msg -> {
                            msg.updateVoiceTask(task);
                            messageRepository.save(msg);
                        });
                    });
                    return;
                }

                // 즉시 완료된 경우
                Message message = messageRepository.findById(messageId).orElse(null);
                if (message != null) {
                    String voiceKey = voiceService.extractS3Key(voiceUrl);
                    message.updateVoiceUrl(voiceKey);
                    messageRepository.save(message);

                    // 로그 저장
                    VoiceLog voiceLog = VoiceLog.builder()
                            .groupId(groupId)
                            .voiceId(messageId)
                            .actionType(ActionType.ADD)
                            .build();
                    voiceLogRepository.save(voiceLog);
                    iotSyncService.notifyUpdate(groupId, "voice");

                    log.info("[TTS Async] messageId: {} 에 대한 TTS 처리 완료", messageId);
                } else {
                    log.error("[TTS Async] 메시지를 찾을 수 없습니다: id={}", messageId);
                }
            }
        } catch (Exception e) {
            log.error("[TTS Async] TTS 생성 중 에러 발생 (messageId: {}): {}", messageId, e.getMessage());
        }
    }
}
