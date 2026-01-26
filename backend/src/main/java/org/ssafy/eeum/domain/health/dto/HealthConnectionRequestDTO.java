package org.ssafy.eeum.domain.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HealthConnectionRequestDTO {

    @Schema(description = "서비스 제공자", example = "SAMSUNG_HEALTH")
    @NotBlank(message = "제공자 정보는 필수입니다.")
    private String provider;

    @Schema(description = "권한 상태", example = "GRANTED", allowableValues = {"GRANTED", "REVOKED", "ERROR"})
    @NotBlank(message = "권한 상태는 필수입니다.")
    private String permissionStatus;
}