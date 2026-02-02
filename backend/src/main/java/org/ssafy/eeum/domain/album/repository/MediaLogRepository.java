package org.ssafy.eeum.domain.album.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.album.entity.MediaLog;

import java.util.List;

public interface MediaLogRepository extends JpaRepository<MediaLog, Integer> {
    List<MediaLog> findByGroupIdAndIdGreaterThan(Integer groupId, Integer lastLogId);
}
