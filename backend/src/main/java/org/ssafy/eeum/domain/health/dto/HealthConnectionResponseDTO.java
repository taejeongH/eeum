package org.ssafy.eeum.domain.health.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthConnectionResponseDTO {

    private String provider;
    private String status;
    private LocalDateTime connectedAt;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime updatedAt;

    public static HealthConnectionResponseDTO ofUpdate(String status, LocalDateTime updatedAt) {
        return HealthConnectionResponseDTO.builder()
                .status(status)
                .updatedAt(updatedAt)
                .build();
    }
}