package org.ssafy.eeum.domain.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;

import org.ssafy.eeum.domain.voice.entity.VoiceTask;
import java.util.List;
import java.util.Optional;

public interface VoiceSampleRepository extends JpaRepository<VoiceSample, Integer> {
    List<VoiceSample> findAllByUserId(Integer userId);

    List<VoiceSample> findAllByUserIdOrderByCreatedAtDesc(Integer userId);

    long countByUserId(Integer userId);

    Optional<VoiceSample> findByVoiceTask(VoiceTask voiceTask);

    Optional<VoiceSample> findTopByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<VoiceSample> findBySamplePath(String samplePath);
}