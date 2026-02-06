package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.health.repository.HealthMetricRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthMetricPersistenceService {

    private final HealthMetricRepository healthMetricRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAllWithNewTransaction(List<HealthMetric> metrics) {
        healthMetricRepository.saveAll(metrics);
    }
}
