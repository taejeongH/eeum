package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * eeum/device/{device_id}/update 토픽용 DTO (Server -> IoT)
 * 서버에서 IoT로 업데이트 알림 전송
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttDeviceUpdateNotificationDTO {

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("update_cnt")
    private Integer updateCnt;

    @JsonProperty("sent_at")
    private Double sentAt;
}
