package org.ssafy.eeum.domain.medication.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.medication.entity.CycleType;
import org.ssafy.eeum.domain.medication.entity.MedicationPlan;
import org.ssafy.eeum.domain.medication.entity.MedicationPlanTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class MedicationResponse {
    private Long id;
    private Long groupId;
    private String medicineName;
    private CycleType cycleType;
    private int totalDosesDay;
    private String cycleValue;
    private int daysOfWeek;
    private LocalDate startDate;
    private LocalDate endDate;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "HH:mm", timezone = "Asia/Seoul")
    private List<LocalTime> notificationTimes;

    public static MedicationResponse from(MedicationPlan medicationPlan) {
        return MedicationResponse.builder()
                .id(medicationPlan.getId())
                .groupId(medicationPlan.getGroupId())
                .medicineName(medicationPlan.getMedicineName())
                .cycleType(medicationPlan.getCycleType())
                .totalDosesDay(medicationPlan.getTotalDosesDay())
                .cycleValue(medicationPlan.getCycleValue())
                .daysOfWeek(medicationPlan.getDaysOfWeek())
                .startDate(medicationPlan.getStartDate())
                .endDate(medicationPlan.getEndDate())
                .notificationTimes(
                        medicationPlan.getMedicationPlanTimes().stream()
                                .map(MedicationPlanTime::getNotificationTime)
                                .collect(Collectors.toList())
                )
                .build();
    }
}
