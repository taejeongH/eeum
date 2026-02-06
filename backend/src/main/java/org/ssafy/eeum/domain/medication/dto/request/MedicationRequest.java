package org.ssafy.eeum.domain.medication.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.medication.entity.CycleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class MedicationRequest {
    private String medicineName;
    private CycleType cycleType;
    private int totalDosesDay;
    private String cycleValue;
    private int daysOfWeek;
    private LocalDate startDate;
    private LocalDate endDate;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "HH:mm", timezone = "Asia/Seoul")
    private List<LocalTime> notificationTimes;
}
