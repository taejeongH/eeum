package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * eeum/event 토픽용 DTO (IoT -> Server)
 * 센서 이벤트 메시지 (vision/pir)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttEventMessageDTO {

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("event")
    private String event;

    @JsonProperty("started_at")
    private Double startedAt;

    @JsonProperty("detected_at")
    private Double detectedAt;

    @JsonProperty("token")
    private String token;
}
