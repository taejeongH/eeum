package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * eeum/status 토픽용 DTO (IoT -> Server)
 * 디바이스 온라인/오프라인 상태 메시지
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttStatusMessageDTO {

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("link")
    private List<DeviceLink> link;

    @JsonProperty("detected_at")
    private Double detectedAt;

    @JsonProperty("token")
    private String token;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceLink {
        @JsonProperty("id")
        private String id;

        @JsonProperty("alive")
        private Boolean alive;
    }
}
