package org.ssafy.eeum.domain.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.health.entity.HealthConnection;

import java.util.Optional;

public interface HealthConnectionRepository extends JpaRepository<HealthConnection, Integer> {

    Optional<HealthConnection> findByFamilyIdAndProvider(Long familyId, String provider);
}