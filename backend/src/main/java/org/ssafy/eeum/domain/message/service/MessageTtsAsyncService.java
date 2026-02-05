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

    @Async
    @Transactional
    public void processTtsAsync(Integer messageId, Integer userId, String content, Integer groupId) {
        log.info("[TTS Async] messageId: {} 에 대한 TTS 생성 및 처리 시작", messageId);

        try {
            // 1. TTS 생성 요청 (메시지 ID 포함)
            String voiceUrl = voiceService.createTtsUrl(userId, content, messageId);

            if (voiceUrl != null) {
                // 1.5. 즉시 완료되지 않은 경우 (Job ID가 반환된 경우)
                if (!voiceUrl.startsWith("http")) {
                    String jobId = voiceUrl;
                    log.info("[TTS Async] messageId: {} 에 대한 TTS 생성이 지연되어 대기 상태(Job ID: {})로 전환합니다.", messageId, jobId);

                    messageRepository.findById(messageId).ifPresent(msg -> {
                        msg.updateTtsJobId(jobId);
                        messageRepository.save(msg);
                    });
                    return;
                }

                // 2. 즉시 완료된 경우 (Warm start)
                Message message = messageRepository.findById(messageId).orElse(null);
                if (message != null) {
                    String voiceKey = voiceService.extractS3Key(voiceUrl);
                    message.updateVoiceUrl(voiceKey);
                    messageRepository.save(message); // 명시적 저장이 transactional 안에서 필요할 수 있음

                    // 3. 로그 저장
                    VoiceLog voiceLog = VoiceLog.builder()
                            .groupId(groupId)
                            .voiceId(messageId)
                            .actionType(ActionType.ADD)
                            .build();
                    voiceLogRepository.save(voiceLog);

                    // 4. IoT 동기화 알림 (배치 처리 로직 따름)
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
