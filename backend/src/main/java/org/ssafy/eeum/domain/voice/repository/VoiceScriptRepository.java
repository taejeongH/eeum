package org.ssafy.eeum.domain.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceScript;

import java.util.List;

public interface VoiceScriptRepository extends JpaRepository<VoiceScript, Integer> {
    List<VoiceScript> findAllByOrderByScriptOrderAsc();
}