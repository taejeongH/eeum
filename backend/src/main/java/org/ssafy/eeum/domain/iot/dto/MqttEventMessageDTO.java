package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
