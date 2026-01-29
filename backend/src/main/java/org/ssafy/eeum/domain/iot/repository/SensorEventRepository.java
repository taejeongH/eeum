package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.SensorEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SensorEventRepository extends JpaRepository<SensorEvent, Integer> {

    // 이벤트 ID로 중복 체크
    boolean existsByEventId(String eventId);

    Optional<SensorEvent> findByEventId(String eventId);

    // 미처리 이벤트 조회
    List<SensorEvent> findByProcessedFalseOrderByDetectedAtDesc();

    // 특정 그룹의 이벤트 조회
    List<SensorEvent> findByFamilyIdOrderByDetectedAtDesc(Integer familyId);

    // 특정 기간 내 이벤트 조회
    List<SensorEvent> findByDetectedAtBetween(LocalDateTime start, LocalDateTime end);
}
