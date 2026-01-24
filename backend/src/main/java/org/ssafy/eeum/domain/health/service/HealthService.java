package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.health.dto.HealthMetricRequestDTO;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.health.repository.HealthMetricRepository;
import org.ssafy.eeum.domain.user.entity.User;
import org.ssafy.eeum.domain.user.repository.UserRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.mqtt.MqttService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthService {

    private final HealthMetricRepository healthMetricRepository;
    private final UserRepository userRepository;
    private final MqttService mqttService;

    @Transactional
    public void saveHealthMetrics(Integer userId, List<HealthMetricRequestDTO> requests) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        List<HealthMetric> metrics = requests.stream()
                .map(dto -> dto.toEntity(user))
                .toList();

        healthMetricRepository.saveAll(metrics);
    }
}
