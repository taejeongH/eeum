package org.ssafy.eeum.domain.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;

import java.util.List;

public interface VoiceSampleRepository extends JpaRepository<VoiceSample, Integer> {
    List<VoiceSample> findAllByUserId(Integer userId);

    List<VoiceSample> findAllByUserIdOrderByCreatedAtDesc(Integer userId);

    long countByUserId(Integer userId);
}