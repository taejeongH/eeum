package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class IotDevicePairRequestDTO {
    @Schema(description = "페어링 코드", example = "D9E6F893")
    @JsonProperty("pairing_code")
    private String pairingCode;

    @Schema(description = "마스터 기기 시리얼 번호 (토큰 발급 대상)", example = "JETSON-MASTER-001")
    @JsonProperty("master_serial")
    private String masterSerialNumber;

    @Schema(description = "등록할 기기 목록")
    @JsonProperty("devices")
    private List<IotDeviceInfoResponseDTO> devices;
}
