package org.ssafy.eeum.domain.health.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.auth.entity.User;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class HealthMetricRequestDTO {
    private LocalDateTime recordDate;
    private Integer steps;
    private Integer floorsClimbed;
    private Integer restingHeartRate;
    private Integer averageHeartRate;
    private Integer maxHeartRate;
    private Integer sleepTotalMinutes;
    private Integer sleepDeepMinutes;
    private Integer sleepLightMinutes;
    private Integer sleepRemMinutes;
    private Integer bloodOxygen;
    private Integer bloodGlucose;
    private Integer systolicPressure;
    private Integer diastolicPressure;
    private Double bodyTemperature;

    public HealthMetric toEntity(User user) {
        return HealthMetric.builder()
                .user(user)
                .recordDate(this.recordDate)
                .steps(this.steps)
                .floorsClimbed(this.floorsClimbed)
                .restingHeartRate(this.restingHeartRate)
                .averageHeartRate(this.averageHeartRate)
                .maxHeartRate(this.maxHeartRate)
                .sleepTotalMinutes(this.sleepTotalMinutes)
                .sleepDeepMinutes(this.sleepDeepMinutes)
                .sleepLightMinutes(this.sleepLightMinutes)
                .sleepRemMinutes(this.sleepRemMinutes)
                .bloodOxygen(this.bloodOxygen)
                .bloodGlucose(this.bloodGlucose)
                .systolicPressure(this.systolicPressure)
                .diastolicPressure(this.diastolicPressure)
                .bodyTemperature(this.bodyTemperature)
                .build();
    }
}