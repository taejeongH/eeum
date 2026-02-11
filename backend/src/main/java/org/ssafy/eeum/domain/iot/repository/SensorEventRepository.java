package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.SensorEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 센서 감지 이벤트(SensorEvent) 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 기기별, 가족별 센서 로그 조회 및 미처리 이벤트 필터링을 지원합니다.
 * 
 * @summary 센서 이벤트 레포지토리
 */
public interface SensorEventRepository extends JpaRepository<SensorEvent, Integer> {

    /**
     * 특정 이벤트 고유 식별자가 존재하는지 확인합니다.
     * 
     * @summary 이벤트 중복 여부 확인
     * @param eventId 이벤트 고유 식별자
     * @return 존재 여부
     */
    boolean existsByEventId(String eventId);

    /**
     * 이벤트 고유 식별자를 기준으로 센서 이벤트를 조회합니다.
     * 
     * @summary 이벤트 식별자 기반 조회
     * @param eventId 이벤트 고유 식별자
     * @return 센서 이벤트 정보 (Optional)
     */
    Optional<SensorEvent> findByEventId(String eventId);

    /**
     * 아직 후속 처리가 되지 않은 모든 센서 이벤트를 감지 시간 최신순으로 조회합니다.
     * 
     * @summary 미처리 센서 이벤트 목록 조회
     * @return 센서 이벤트 리스트
     */
    List<SensorEvent> findByProcessedFalseOrderByDetectedAtDesc();

    /**
     * 특정 가족 그룹에서 발생한 센서 이벤트 이력을 최신순으로 조회합니다.
     * 
     * @summary 가족별 센서 이벤트 이력 조회
     * @param familyId 가족 그룹 식별자
     * @return 센서 이벤트 리스트
     */
    List<SensorEvent> findByFamilyIdOrderByDetectedAtDesc(Integer familyId);

    /**
     * 특정 기간 사이에 발생한 센서 이벤트를 조회합니다.
     * 
     * @summary 기간별 센서 이벤트 조회
     * @param start 시작 시간
     * @param end   종료 시간
     * @return 센서 이벤트 리스트
     */
    List<SensorEvent> findByDetectedAtBetween(LocalDateTime start, LocalDateTime end);
}
