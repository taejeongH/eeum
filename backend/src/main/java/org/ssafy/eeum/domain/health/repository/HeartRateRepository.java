package org.ssafy.eeum.domain.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.health.entity.HeartRate;


public interface HeartRateRepository extends JpaRepository<HeartRate, Long> {

    @Query("SELECT new org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO(" +
           "AVG(h.avgRate), MIN(h.minRate), MAX(h.maxRate), COUNT(h)) " +
           "FROM HeartRate h WHERE h.fallEvent.id = :eventId")
    org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO findAggregatedMetricsByFallEventId(@Param("eventId") Integer eventId);

    
    @Query("SELECT new org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO(" +
           "CAST(h.avgRate AS double), h.minRate, h.maxRate, 1L) " +
           "FROM HeartRate h WHERE h.family.id = :familyId ORDER BY h.measuredAt DESC LIMIT 1")
    java.util.Optional<org.ssafy.eeum.domain.health.dto.HeartRateResponseDTO> findLatestByFamilyId(@Param("familyId") Integer familyId);

    
    java.util.Optional<HeartRate> findFirstByFamilyIdOrderByMeasuredAtDesc(Integer familyId);
}
