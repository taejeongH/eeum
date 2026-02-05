package org.ssafy.eeum.domain.voice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.domain.voice.service.VoiceAiClient;
import org.ssafy.eeum.domain.voice.service.VoiceService;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsCleanupScheduler {

    private final MessageRepository messageRepository;
    private final VoiceAiClient voiceAiClient;
    private final VoiceService voiceService;
    private final VoiceLogRepository voiceLogRepository;
    private final IotSyncService iotSyncService;

    /**
     * TTS 웹후크 대용 폴링 스케줄러 (적응형 백오프 적용)
     */
    @Scheduled(fixedDelay = 10000) // 10초마다 실행 (초기 응답성 강화)
    @Transactional
    public void cleanupTtsJobs() {
        List<Message> pendingMessages = messageRepository.findByTtsJobIdIsNotNullAndVoiceUrlIsNull();

        if (pendingMessages.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (Message message : pendingMessages) {
            if (!shouldPoll(message, now)) {
                continue;
            }

            String jobId = message.getTtsJobId();
            try {
                message.incrementPollCount();
                messageRepository.save(message);

                String resultOrStatus = voiceAiClient.checkJobStatus(jobId);

                if (resultOrStatus == null || "FAILED".equals(resultOrStatus) || "ERROR".equals(resultOrStatus)) {
                    log.error("[TTS Cleanup] messageId: {} (Job: {}) 가 실패 상태입니다.", message.getId(), jobId);
                    if (message.getTtsPollCount() >= 50) {
                        log.warn("[TTS Cleanup] messageId: {} 폴링 횟수 초과로 포기합니다.", message.getId());
                        message.updateTtsJobId(null); // 추적 중단
                    }
                    continue;
                }

                if (resultOrStatus.startsWith("http")) {
                    log.info("[TTS Cleanup] messageId: {} 완료! (시도 횟수: {})", message.getId(), message.getTtsPollCount());

                    String voiceKey = voiceService.extractS3Key(resultOrStatus);
                    message.updateVoiceUrl(voiceKey);
                    messageRepository.save(message);

                    VoiceLog voiceLog = VoiceLog.builder()
                            .groupId(message.getGroup().getId())
                            .voiceId(message.getId())
                            .actionType(ActionType.ADD)
                            .build();
                    voiceLogRepository.save(voiceLog);

                    iotSyncService.notifyUpdate(message.getGroup().getId(), "voice");
                } else if (message.getTtsPollCount() >= 100) {
                    log.warn("[TTS Cleanup] messageId: {} 가 너무 오래 걸려 추적을 중단합니다.", message.getId());
                    message.updateTtsJobId(null);
                }
            } catch (Exception e) {
                log.error("[TTS Cleanup] Job {} 확인 중 에러: {}", jobId, e.getMessage());
            }
        }
    }

    /**
     * 대기 시간에 따른 폴링 여부 결정 (Adaptive Backoff)
     * - 5회 미만: 매번 (1분)
     * - 20회 미만: 5분에 한 번
     * - 50회 미만: 30분에 한 번
     * - 그 이상: 1시간에 한 번
     */
    private boolean shouldPoll(Message message, LocalDateTime now) {
        if (message.getLastTtsPolledAt() == null)
            return true;

        long secondsSinceLastPoll = Duration.between(message.getLastTtsPolledAt(), now).toSeconds();
        int count = (message.getTtsPollCount() == null) ? 0 : message.getTtsPollCount();

        // 1단계: 초기 1분간 (6회) - 매 10초마다 확인
        if (count < 6)
            return secondsSinceLastPoll >= 10;

        // 2단계: 이후 4분간 (4회, 누적 5분, 총 10회) - 1분에 한 번 확인
        if (count < 10)
            return secondsSinceLastPoll >= 60;

        // 3단계: 5분 ~ 55분 (누적 약 20회) - 5분에 한 번 확인
        if (count < 20)
            return secondsSinceLastPoll >= 300;

        // 4단계: 그 이후 - 1시간에 한 번 확인
        return secondsSinceLastPoll >= 3600;
    }
}
