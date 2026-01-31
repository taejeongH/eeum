package org.ssafy.eeum.domain.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ssafy.eeum.domain.health.entity.HealthMetric;

import org.ssafy.eeum.domain.family.entity.Family;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HealthMetricRepository extends JpaRepository<HealthMetric, Integer> {
    List<HealthMetric> findByFamilyAndRecordDateBetween(Family family, LocalDateTime start, LocalDateTime end);
}
