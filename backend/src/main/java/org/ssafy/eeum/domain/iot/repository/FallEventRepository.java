package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.FallEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ssafy.eeum.domain.iot.entity.FallEvent.StatusType;

public interface FallEventRepository extends JpaRepository<FallEvent, Integer> {
    Optional<FallEvent> findByVideoPath(String videoPath);

    Optional<FallEvent> findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(Integer familyId, StatusType statusType);

    List<FallEvent> findByStatusTypeAndCreatedAtBefore(StatusType statusType, LocalDateTime createdAt);
}
