package org.ssafy.eeum.domain.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceTask;
import java.util.List;
import java.util.Optional;

public interface VoiceTaskRepository extends JpaRepository<VoiceTask, Integer> {
    List<VoiceTask> findByUserId(Integer userId);

    List<VoiceTask> findByStatusInAndJobIdIsNotNull(List<VoiceTask.TaskStatus> statuses);

    List<VoiceTask> findByStatusInAndJobIdIsNotNullAndTypeIn(List<VoiceTask.TaskStatus> statuses,
            List<VoiceTask.TaskType> types);

    Optional<VoiceTask> findByJobId(String jobId);
}