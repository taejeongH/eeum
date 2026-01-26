package org.ssafy.eeum.domain.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceModel;

import java.util.Optional;

public interface VoiceModelRepository extends JpaRepository<VoiceModel, Integer> {
    Optional<VoiceModel> findByUserId(Integer userId);
}