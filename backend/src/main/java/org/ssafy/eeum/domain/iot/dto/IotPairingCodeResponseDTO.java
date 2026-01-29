package org.ssafy.eeum.domain.iot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IotPairingCodeResponseDTO {
    @Schema(description = "페어링 코드", example = "A1B2C3D4")
    private String pairingCode;

    @Schema(description = "만료 시간 (초)", example = "300")
    private long expiresIn;

    @Schema(description = "QR 코드 생성을 위한 완성된 JSON 데이터", example = "{\\\"svc\\\":\\\"eeum\\\",\\\"code\\\":\\\"A1B2C3D4\\\",\\\"ts\\\":1706511380}")
    private String qrContent;
}
