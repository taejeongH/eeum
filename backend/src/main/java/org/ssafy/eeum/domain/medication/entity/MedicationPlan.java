package org.ssafy.eeum.domain.medication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "medication_plans")
public class MedicationPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "medicine_name", nullable = false, length = 100)
    private String medicineName;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false)
    private CycleType cycleType;

    @Column(name = "total_doses_day", nullable = false)
    private int totalDosesDay;

    @Column(name = "cycle_value", length = 50)
    private String cycleValue;

    @Column(name = "days_of_week")
    private int daysOfWeek;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @OneToMany(mappedBy = "medicationPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicationPlanTime> medicationPlanTimes = new ArrayList<>();

    @Builder
    public MedicationPlan(Long groupId, String medicineName, CycleType cycleType, int totalDosesDay, String cycleValue, int daysOfWeek, LocalDate startDate, LocalDate endDate) {
        this.groupId = groupId;
        this.medicineName = medicineName;
        this.cycleType = cycleType;
        this.totalDosesDay = totalDosesDay;
        this.cycleValue = cycleValue;
        this.daysOfWeek = daysOfWeek;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void addMedicationPlanTime(MedicationPlanTime medicationPlanTime) {
        this.medicationPlanTimes.add(medicationPlanTime);
        medicationPlanTime.setMedicationPlan(this);
    }
    
    // Convenience method for Service
    public void addNotificationTime(java.time.LocalTime time) {
        MedicationPlanTime planTime = MedicationPlanTime.builder()
                .notificationTime(time)
                .build();
        addMedicationPlanTime(planTime);
    }

    public void update(String medicineName, CycleType cycleType, String cycleValue, int daysOfWeek, int totalDosesDay, LocalDate startDate, LocalDate endDate) {
        this.medicineName = medicineName;
        this.cycleType = cycleType;
        this.cycleValue = cycleValue;
        this.daysOfWeek = daysOfWeek;
        this.totalDosesDay = totalDosesDay;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    public List<org.ssafy.eeum.domain.medication.entity.MedicationPlanTime> getNotificationTimes() {
        return this.medicationPlanTimes;
    }
}
