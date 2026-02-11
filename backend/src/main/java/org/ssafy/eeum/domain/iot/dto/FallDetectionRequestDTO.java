package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * IoT 기기로부터 수신되는 낙상 감지 이벤트 요청 DTO 클래스입니다.
 * 감지 위치, 심각도 레벨 및 영상 분석 결과 등을 포함합니다.
 * 
 * @summary 낙상 감지 요청 DTO
 */
@Getter
@NoArgsConstructor
public class FallDetectionRequestDTO {
    private String kind;

    @JsonProperty("device_id")
    private String deviceId;

    private FallData data;

    @JsonProperty("detected_at")
    private Double detectedAt;

    /**
     * 낙상 감지의 상세 데이터 정보입니다.
     */
    @Getter
    @NoArgsConstructor
    public static class FallData {
        @JsonProperty("location_id")
        private String locationId;

        private String event;

        /**
         * 위험도 레벨 (1: 위급 등)
         */
        private Integer level;

        /**
         * 감지 신뢰도 (0.0 ~ 1.0)
         */
        private Double confidence;

        @JsonProperty("has_person")
        private Boolean hasPerson;
    }
}
