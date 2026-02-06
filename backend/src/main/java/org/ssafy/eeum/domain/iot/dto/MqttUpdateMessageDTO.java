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
public class MqttUpdateMessageDTO {

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("update_cnt")
    private Integer updateCnt;

    @JsonProperty("updated_at")
    private Double updatedAt;

    @JsonProperty("token")
    private String token;
}
