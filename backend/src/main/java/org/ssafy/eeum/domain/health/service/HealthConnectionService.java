package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.health.dto.HealthConnectionRequestDTO;
import org.ssafy.eeum.domain.health.dto.HealthConnectionResponseDTO;
import org.ssafy.eeum.domain.health.entity.HealthConnection;
import org.ssafy.eeum.domain.health.repository.HealthConnectionRepository;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthConnectionService {

    private final HealthConnectionRepository healthConnectionRepository;
    private final UserRepository userRepository;

    @Transactional
    public HealthConnectionResponseDTO registerConnection(Integer userId, HealthConnectionRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        HealthConnection.ConnectionStatus status = "GRANTED".equals(request.getPermissionStatus())
                ? HealthConnection.ConnectionStatus.CONNECTED
                : HealthConnection.ConnectionStatus.DISCONNECTED;

        HealthConnection connection = healthConnectionRepository.findByUserIdAndProvider(userId, request.getProvider())
                .orElseGet(() -> HealthConnection.builder()
                        .user(user)
                        .provider(request.getProvider())
                        .build());

        connection.updateStatus(status);
        if (status == HealthConnection.ConnectionStatus.CONNECTED) {
            connection.sync();
        }

        healthConnectionRepository.save(connection);

        return HealthConnectionResponseDTO.builder()
                .status(connection.getStatus().name())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public HealthConnectionResponseDTO getConnectionStatus(Integer userId, String provider) {
        HealthConnection connection = healthConnectionRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "연동 정보를 찾을 수 없습니다."));

        return HealthConnectionResponseDTO.builder()
                .provider(connection.getProvider())
                .status(connection.getStatus().name())
                .connectedAt(connection.getCreatedAt())
                .lastSyncedAt(connection.getLastSyncedAt())
                .build();
    }
}