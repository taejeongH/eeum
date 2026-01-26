package org.ssafy.eeum.domain.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ssafy.eeum.domain.health.entity.HealthMetric;

@Repository
public interface HealthMetricRepository extends JpaRepository<HealthMetric, Integer> {

}
