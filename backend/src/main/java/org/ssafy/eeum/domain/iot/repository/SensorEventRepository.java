package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.SensorEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SensorEventRepository extends JpaRepository<SensorEvent, Integer> {

    boolean existsByEventId(String eventId);

    Optional<SensorEvent> findByEventId(String eventId);

    List<SensorEvent> findByProcessedFalseOrderByDetectedAtDesc();

    List<SensorEvent> findByFamilyIdOrderByDetectedAtDesc(Integer familyId);

    List<SensorEvent> findByDetectedAtBetween(LocalDateTime start, LocalDateTime end);
}
