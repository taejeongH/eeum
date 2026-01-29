package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * eeum/response 토픽용 DTO (IoT -> Server)
 * 음성 응답 메시지
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttResponseMessageDTO {

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("event")
    private String event;

    @JsonProperty("stt_content")
    private String sttContent;

    @JsonProperty("detected_at")
    private Double detectedAt;

    @JsonProperty("token")
    private String token;
}
