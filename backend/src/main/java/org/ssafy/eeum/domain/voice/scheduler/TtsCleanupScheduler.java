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
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;

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

    private final VoiceLogRepository voiceLogRepository;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void cleanupTtsJobs() {
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
                if (resultOrStatus == null || "ERROR".equals(resultOrStatus)) {
                    log.warn("[TTS Cleanup) Job {} 응답 지연 또는 통신 에러 (상태: {}). 다음 주기에 재시도합니다.", jobId, resultOrStatus);
                    continue;
                }

                if ("FAILED".equals(resultOrStatus)) {
                    log.error("[TTS Cleanup] Job {} 생성 실패 확인.", jobId);
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
                    String s3Key = resultOrStatus;
                    if (task.getType() != VoiceTask.TaskType.TRAINING) {
                        s3Key = voiceService.extractS3Key(resultOrStatus);
                    }

                    task.updateResult(s3Key);
                    taskRepository.save(task);
                    handleTaskCompletion(task);

                } else if (task.getPollCount() >= 150) {
                    task.fail(VoiceTask.TaskStatus.TIMEOUT);
                    taskRepository.save(task);
                } else {
                    if (!resultOrStatus.equals(task.getStatus().name())) {
                        try {
                            task.updateStatus(VoiceTask.TaskStatus.valueOf(resultOrStatus));
                            taskRepository.save(task);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {

            }
        }
    }

    private void handleTaskCompletion(VoiceTask task) {
        if (task.getType() == VoiceTask.TaskType.MESSAGE) {
            Message message = messageRepository.findByVoiceTask(task).orElse(null);
            if (message != null) {
                message.updateVoiceUrl(task.getResultUrl());
                messageRepository.save(message);

                // [ADDED] VoiceLog Creation for Polling Logic
                org.ssafy.eeum.domain.voice.entity.VoiceLog voiceLog = org.ssafy.eeum.domain.voice.entity.VoiceLog
                        .builder()
                        .groupId(message.getGroup().getId())
                        .voiceId(message.getId())
                        .actionType(ActionType.ADD)
                        .build();
                voiceLogRepository.save(voiceLog);

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

        if (count < 10)
            return secondsSinceLastPoll >= 10; // 초기 10회는 10초마다
        if (count < 30)
            return secondsSinceLastPoll >= 60; // 이후 20회는 1분마다
        if (count < 60)
            return secondsSinceLastPoll >= 300; // 이후 30회는 5분마다
        return secondsSinceLastPoll >= 3600; // 그 이후는 한 시간마다
    }
}
