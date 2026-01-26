package org.ssafy.eeum.domain.iot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IotDeviceUpdateDTO {

    @Schema(description = "변경할 기기 별칭", example = "안방 카메라")
    private String deviceName;

    @Schema(description = "변경할 설치 위치", example = "BEDROOM")
    private String locationType;
}
