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
import org.ssafy.eeum.domain.voice.entity.VoiceTask;
import org.ssafy.eeum.domain.voice.repository.VoiceTaskRepository;
import org.ssafy.eeum.domain.voice.service.VoiceAiClient;
import org.ssafy.eeum.domain.voice.service.VoiceService;
import org.ssafy.eeum.domain.voice.repository.VoiceSampleRepository;

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
    private final VoiceTaskRepository taskRepository;
    private final IotSyncService iotSyncService;
    private final VoiceSampleRepository voiceSampleRepository;

    /**
     * TTS 웹후크 대용 폴링 스케줄러 (적응형 백오프 적용)
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void cleanupTtsJobs() {
        // IN_QUEUE 거나 IN_PROGRESS 인 작업만 조회
        List<VoiceTask> pendingTasks = taskRepository.findByStatusInAndJobIdIsNotNull(
                List.of(VoiceTask.TaskStatus.IN_QUEUE, VoiceTask.TaskStatus.IN_PROGRESS));

        if (pendingTasks.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (VoiceTask task : pendingTasks) {
            if (!shouldPoll(task, now)) {
                continue;
            }

            String jobId = task.getJobId();
            try {
                task.incrementPollCount();
                taskRepository.save(task);

                String resultOrStatus = voiceAiClient.checkJobStatus(jobId);

                // 실패 처리
                if (resultOrStatus == null || "FAILED".equals(resultOrStatus) || "ERROR".equals(resultOrStatus)) {
                    log.error("[Voice Task] Task {} (Job: {}) 가 실패 상태입니다.", task.getId(), jobId);
                    if (task.getPollCount() >= 100) {
                        task.fail(VoiceTask.TaskStatus.TIMEOUT);
                    } else {
                        task.updateStatus(VoiceTask.TaskStatus.FAILED);
                    }
                    taskRepository.save(task);
                    continue;
                }

                // 완료 처리
                if (resultOrStatus.startsWith("http")) {
                    log.info("[Voice Task] Task {} 완료! (유형: {}, 시도: {})", task.getId(), task.getType(),
                            task.getPollCount());

                    String s3Key = resultOrStatus;
                    if (task.getType() != VoiceTask.TaskType.TRAINING) {
                        s3Key = voiceService.extractS3Key(resultOrStatus);
                    }

                    task.updateResult(s3Key);
                    taskRepository.save(task);

                    // 연관된 엔티티에 결과 전파 (만약 필요하다면)
                    handleTaskCompletion(task);

                } else if (task.getPollCount() >= 150) { // 모델 학습 등을 고려하여 넉넉히
                    log.warn("[Voice Task] Task {} 가 너무 오래 걸려 중단합니다.", task.getId());
                    task.fail(VoiceTask.TaskStatus.TIMEOUT);
                    taskRepository.save(task);
                } else {
                    // 진행 중 상태 업데이트
                    if (!resultOrStatus.equals(task.getStatus().name())) {
                        try {
                            task.updateStatus(VoiceTask.TaskStatus.valueOf(resultOrStatus));
                            taskRepository.save(task);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Voice Task] Job {} 확인 중 에러: {}", jobId, e.getMessage());
            }
        }
    }

    private void handleTaskCompletion(VoiceTask task) {
        if (task.getType() == VoiceTask.TaskType.MESSAGE) {
            Message message = messageRepository.findByVoiceTask(task).orElse(null);
            if (message != null) {
                message.updateVoiceUrl(task.getResultUrl());
                messageRepository.save(message);
                iotSyncService.notifyUpdate(message.getGroup().getId(), "voice");
            }
        } else if (task.getType() == VoiceTask.TaskType.SAMPLE) {
            org.ssafy.eeum.domain.voice.entity.VoiceSample sample = voiceSampleRepository.findByVoiceTask(task)
                    .orElse(null);
            if (sample != null) {
                sample.completeTts(task.getResultUrl());
                voiceSampleRepository.save(sample);
            }
        }
    }

    private boolean shouldPoll(VoiceTask task, LocalDateTime now) {
        if (task.getLastPolledAt() == null)
            return true;

        long secondsSinceLastPoll = Duration.between(task.getLastPolledAt(), now).toSeconds();
        int count = (task.getPollCount() == null) ? 0 : task.getPollCount();

        // 일원화된 백오프 정책
        if (count < 10)
            return secondsSinceLastPoll >= 10; // 초기 10회는 10초마다 (약 1.5분)
        if (count < 30)
            return secondsSinceLastPoll >= 60; // 이후 20회는 1분마다 (약 20분)
        if (count < 60)
            return secondsSinceLastPoll >= 300; // 이후 30회는 5분마다 (약 2.5시간)
        return secondsSinceLastPoll >= 3600; // 그 이후는 한 시간마다
    }
}
