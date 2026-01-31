package org.ssafy.eeum.domain.health.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.family.entity.Family;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class HealthMetricRequestDTO {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]")
    private LocalDateTime recordDate;
    private Integer steps;
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

    private Integer activeCalories;
    private Integer activeMinutes;

    public HealthMetric toEntity(Family family) {
        return HealthMetric.builder()
                .family(family)
                .recordDate(this.recordDate)
                .steps(this.steps)
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

                .activeCalories(this.activeCalories)
                .activeMinutes(this.activeMinutes)
                .build();
    }
}