package org.ssafy.eeum.domain.iot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "IoT 스트리밍 IP 업데이트 요청 DTO")
public class IotStreamingIpRequestDTO {
    @Schema(description = "IoT 기기 IP 주소", example = "192.168.1.10")
    private String ipAddress;

    public IotStreamingIpRequestDTO(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
