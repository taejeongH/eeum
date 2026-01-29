package org.ssafy.eeum.domain.schedule.dto;

import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.schedule.entity.CategoryType;
import org.ssafy.eeum.domain.schedule.entity.RepeatType;

import java.time.LocalDateTime;

@Getter
@Builder
public class ScheduleResponseDTO {
    private String scheduleId; // 가상 ID
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private CategoryType categoryType;
    private String description;
    private String visitorName;
    private String visitPurpose;
    private Boolean isVisited;
    private RepeatType repeatType;
    private Boolean isLunar;
    private Boolean isModified;
    private String targetPerson;
}