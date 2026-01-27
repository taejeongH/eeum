package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.health.dto.HealthConnectionRequestDTO;
import org.ssafy.eeum.domain.health.dto.HealthConnectionResponseDTO;
import org.ssafy.eeum.domain.health.entity.HealthConnection;
import org.ssafy.eeum.domain.health.repository.HealthConnectionRepository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthConnectionService {

        private final HealthConnectionRepository healthConnectionRepository;
        private final FamilyRepository familyRepository;

        @Transactional
        public HealthConnectionResponseDTO registerConnection(Long groupId,
                        HealthConnectionRequestDTO request) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                HealthConnection.ConnectionStatus status = "GRANTED".equals(request.getPermissionStatus())
                                ? HealthConnection.ConnectionStatus.CONNECTED
                                : HealthConnection.ConnectionStatus.DISCONNECTED;

                HealthConnection connection = healthConnectionRepository
                                .findByFamilyIdAndProvider(family.getId(), request.getProvider())
                                .orElseGet(() -> HealthConnection.builder()
                                                .family(family)
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

        public HealthConnectionResponseDTO getConnectionStatus(Long groupId, String provider) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                HealthConnection connection = healthConnectionRepository
                                .findByFamilyIdAndProvider(family.getId(), provider)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "연동 정보를 찾을 수 없습니다."));

                return HealthConnectionResponseDTO.builder()
                                .provider(connection.getProvider())
                                .status(connection.getStatus().name())
                                .connectedAt(connection.getCreatedAt())
                                .lastSyncedAt(connection.getLastSyncedAt())
                                .build();
        }
}
