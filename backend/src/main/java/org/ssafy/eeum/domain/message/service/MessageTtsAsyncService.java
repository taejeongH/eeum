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
import org.ssafy.eeum.domain.voice.repository.VoiceTaskRepository;
import org.ssafy.eeum.domain.voice.service.VoiceService;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTtsAsyncService {

    private final VoiceService voiceService;
    private final MessageRepository messageRepository;
    private final VoiceLogRepository voiceLogRepository;
    private final IotSyncService iotSyncService;
    private final VoiceTaskRepository taskRepository;

    @Async
    @Transactional
    public void processTtsAsync(Integer messageId, Integer userId, String content, Integer groupId) {
        String result = null;

        try {
            // TTS 생성을 위한 URL 요청
            result = voiceService.createTtsUrl(userId, content, messageId);
        } catch (Exception e) {
            log.error("[TTS 비동기] TTS 생성 요청 실패: {}", e.getMessage());
            return;
        }

        try {
            // 케이스 1: 비동기 처리 (작업 ID 반환)
            if (result != null && result.startsWith("TASK_ID:")) {
                Integer taskId = Integer.parseInt(result.substring(8));
                log.info("[TTS 비동기] 메시지 ID: {} TTS 생성 요청됨 (작업 ID: {}). 웹후크 대기 중.",
                        messageId, taskId);

                taskRepository.findById(taskId).ifPresent(task -> {
                    messageRepository.findById(messageId).ifPresent(msg -> {
                        msg.updateVoiceTask(task);
                        messageRepository.save(msg);
                    });
                });
                // 즉시 반환. VoiceWebhookController에서 완료 처리 및 알림을 담당합니다.
                return;
            }

            // 케이스 2: 즉시 완료 또는 실패 시 폴백
            Message message = messageRepository.findById(messageId).orElse(null);
            if (message != null) {
                if (result != null && result.startsWith("http")) {
                    // 즉시 성공 시 S3 키 추출 및 업데이트
                    String voiceKey = voiceService.extractS3Key(result);
                    message.updateVoiceUrl(voiceKey);
                    messageRepository.save(message);

                    log.info("[TTS 비동기] 메시지 처리가 즉시 완료되었습니다. ID: {}", messageId);
                } else {
                    // 실패 또는 텍스트 전용 (결과가 없거나 예상치 못한 형식)
                    log.info("[TTS 비동기] 메시지 처리 중 음성 업데이트 건너뜀 (결과: {}). ID: {}", result,
                            messageId);
                }

                // 음성 로그 생성 및 IoT 알림 전송 (즉시 성공 또는 텍스트 전용 폴백 시 모두 실행)
                VoiceLog voiceLog = VoiceLog.builder()
                        .groupId(groupId)
                        .voiceId(messageId)
                        .actionType(ActionType.ADD)
                        .build();
                voiceLogRepository.save(voiceLog);
                iotSyncService.notifyUpdate(groupId, "voice");
            }
        } catch (Exception e) {
            log.error("[TTS 비동기] 메시지 후처리 중 오류 발생 (메시지 ID: {}): {}", messageId,
                    e.getMessage());
        }
    }
}
