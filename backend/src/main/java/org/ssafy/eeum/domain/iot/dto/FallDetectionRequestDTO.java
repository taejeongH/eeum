package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FallDetectionRequestDTO {
    private String kind;

    @JsonProperty("device_id")
    private String deviceId;

    private FallData data;

    @JsonProperty("detected_at")
    private Double detectedAt;

    @Getter
    @NoArgsConstructor
    public static class FallData {
        @JsonProperty("location_id")
        private String locationId;

        private String event;

        private Integer level;

        @JsonProperty("has_person")
        private Boolean hasPerson;
    }
}
