package org.ssafy.eeum.domain.health.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.health.entity.HealthConnection;
import org.ssafy.eeum.domain.auth.entity.User;

import java.util.Optional;

public interface HealthConnectionRepository extends JpaRepository<HealthConnection, Integer> {

    Optional<HealthConnection> findByUserIdAndProvider(Integer userId, String provider);
}