package org.ssafy.eeum.domain.schedule.dto;

import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.schedule.entity.CategoryType;
import org.ssafy.eeum.domain.schedule.entity.RepeatType;

import java.time.LocalDateTime;

/**
 * 일정 상세 정보 및 목록 조회를 위한 응답 데이터 전달 객체(DTO)입니다.
 * 가상 ID 정보를 포함하여 반복 일정의 개별 발생을 식별할 수 있습니다.
 * 
 * @summary 일정 응답 DTO
 */
@Getter
@Builder
public class ScheduleResponseDTO {
    private String scheduleId;
    private Integer creatorId;
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