package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class MqttAlarmMessageDTO {
    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("content")
    private String content;

    @JsonProperty("data")
    private Map<String, Object> data;

    @JsonProperty("sent_at")
    private double sentAt;
}
