package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * eeum/update 토픽용 DTO (IoT -> Server)
 * 업데이트 확인 메시지
 */
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
