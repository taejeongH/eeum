package org.ssafy.eeum.domain.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO;
import org.ssafy.eeum.domain.health.entity.HeartRate;

import java.util.Optional;

/**
 * 심박수 측정 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 가족별 최신 심박수 조회 및 특정 이벤트 관련 심박수 집계 기능을 제공합니다.
 * 
 * @summary 심박수 레포지토리
 */
public interface HeartRateRepository extends JpaRepository<HeartRate, Long> {

       /**
        * 특정 낙상 이벤트와 연관된 심박수 데이터들을 집계하여 반환합니다.
        * 
        * @summary 이벤트별 심박수 집계 조회
        * @param eventId 낙상 이벤트 식별자
        * @return 집계된 심박수 정보 DTO
        */
       @Query("SELECT new org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO(" +
                     "AVG(h.avgRate), MIN(h.minRate), MAX(h.maxRate), COUNT(h)) " +
                     "FROM HeartRate h WHERE h.fallEvent.id = :eventId")
       HeartRateResponseDTO findAggregatedMetricsByFallEventId(@Param("eventId") Integer eventId);

       /**
        * 특정 가족 그룹의 가장 최신 심박수 데이터를 조회합니다.
        * 
        * @summary 가족별 최신 심박수 조회
        * @param familyId 가족 그룹 식별자
        * @return 최신 심박수 정보 DTO (Optional)
        */
       @Query("SELECT new org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO(" +
                     "CAST(h.avgRate AS double), h.minRate, h.maxRate, 1L) " +
                     "FROM HeartRate h WHERE h.family.id = :familyId ORDER BY h.measuredAt DESC LIMIT 1")
       Optional<HeartRateResponseDTO> findLatestByFamilyId(@Param("familyId") Integer familyId);

       /**
        * 특정 가족 그룹의 가장 최신 심박수 엔티티를 조회합니다. (내부 시각 체크용)
        * 
        * @summary 최신 심박수 엔티티 단건 조회
        * @param familyId 가족 그룹 식별자
        * @return 최신 심박수 엔티티 (Optional)
        */
       Optional<HeartRate> findFirstByFamilyIdOrderByMeasuredAtDesc(Integer familyId);
}
