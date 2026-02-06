package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IotDeviceRefreshRequestDTO {
    @Schema(description = "기기 시리얼 번호", example = "JETSON-ORIN-NANO-001")
    @JsonProperty("serial_number")
    private String serialNumber;

    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @JsonProperty("refresh_token")
    private String refreshToken;
}
