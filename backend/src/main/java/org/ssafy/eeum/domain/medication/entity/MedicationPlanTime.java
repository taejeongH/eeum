package org.ssafy.eeum.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "medication_plan_times")
public class MedicationPlanTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private MedicationPlan medicationPlan;

    @Column(name = "notification_time", nullable = false)
    private LocalTime notificationTime;

    @Builder
    public MedicationPlanTime(LocalTime notificationTime) {
        this.notificationTime = notificationTime;
    }

    public void setMedicationPlan(MedicationPlan medicationPlan) {
        this.medicationPlan = medicationPlan;
    }
}
