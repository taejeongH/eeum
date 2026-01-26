package org.ssafy.eeum.domain.health.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "health_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HealthMetric extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private User user;

    @Column(name = "record_date", nullable = false)
    private LocalDateTime recordDate;

    private Integer steps;

    @Column(name = "floors_climbed")
    private Integer floorsClimbed;

    @Column(name = "resting_heart_rate")
    private Integer restingHeartRate;

    @Column(name = "average_heart_rate")
    private Integer averageHeartRate;

    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "sleep_total_minutes")
    private Integer sleepTotalMinutes;

    @Column(name = "sleep_deep_minutes")
    private Integer sleepDeepMinutes;

    @Column(name = "sleep_light_minutes")
    private Integer sleepLightMinutes;

    @Column(name = "sleep_rem_minutes")
    private Integer sleepRemMinutes;

    @Column(name = "blood_oxygen")
    private Integer bloodOxygen;

    @Column(name = "blood_glucose")
    private Integer bloodGlucose;

    @Column(name = "systolic_pressure")
    private Integer systolicPressure;

    @Column(name = "diastolic_pressure")
    private Integer diastolicPressure;

    @Column(name = "body_temperature")
    private Double bodyTemperature;

    // 심박수가 위험인지 판단하는 기준
    public boolean isHeartRateWarning() {
        return maxHeartRate != null && maxHeartRate >= 120;
    }

    public boolean isBloodPressureWarning() {
        return systolicPressure != null && systolicPressure >= 140;
    }
}
