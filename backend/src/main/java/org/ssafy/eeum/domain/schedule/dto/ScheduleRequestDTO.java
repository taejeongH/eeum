package org.ssafy.eeum.domain.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.schedule.entity.CategoryType;
import org.ssafy.eeum.domain.schedule.entity.RepeatType;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRequestDTO {
    private String title;
    private LocalDate startAt;
    private LocalDate endAt;
    private LocalDate recurrenceEndAt;
    private CategoryType categoryType;
    private String description;
    private String visitorName;
    private String visitPurpose;
    private RepeatType repeatType;
    private Boolean isLunar;
    private String targetPerson;
}