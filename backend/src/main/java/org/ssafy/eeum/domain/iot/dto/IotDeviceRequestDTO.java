package org.ssafy.eeum.domain.iot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IotDeviceRequestDTO {

    @Schema(description = "가족 ID", example = "1")
    private Long groupId;

    @Schema(description = "기기 시리얼 번호", example = "SN-12345678")
    private String serialNumber;

    @Schema(description = "기기 별칭", example = "거실 카메라")
    private String deviceName;

    @Schema(description = "설치 위치", example = "LIVING_ROOM")
    private String locationType;
}
