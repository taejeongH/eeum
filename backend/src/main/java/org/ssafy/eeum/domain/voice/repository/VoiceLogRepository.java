package org.ssafy.eeum.domain.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;

import java.util.List;

public interface VoiceLogRepository extends JpaRepository<VoiceLog, Integer> {
    List<VoiceLog> findByGroupIdAndIdGreaterThan(Integer groupId, Integer lastLogId);
}
