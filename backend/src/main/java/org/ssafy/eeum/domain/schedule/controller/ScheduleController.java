package org.ssafy.eeum.domain.schedule.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.schedule.dto.ScheduleRequestDTO;
import org.ssafy.eeum.domain.schedule.dto.ScheduleResponseDTO;
import org.ssafy.eeum.domain.schedule.service.ScheduleService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Tag(name = "Schedule", description = "가족 일정 관리 API")
@RestController
@RequestMapping("/api/families/{familyId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {
        private final ScheduleService scheduleService;

        @SwaggerApiSpec(summary = "월간 일정 조회", description = "특정 연도/월의 모든 가족 일정을 조회합니다. 카테고리, 키워드, 대상자, 방문 여부로 필터링할 수 있습니다.", successMessage = "월간 일정 조회 성공")
        @GetMapping
        public RestApiResponse<List<ScheduleResponseDTO>> getMonthlySchedules(
                        @PathVariable Integer familyId,
                        @RequestParam int year,
                        @RequestParam int month,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String targetPerson,
                        @RequestParam(required = false) Boolean isVisited) {
                return RestApiResponse
                                .success(scheduleService.getMonthlySchedules(familyId, year, month, category, keyword,
                                                targetPerson,
                                                isVisited));
        }

        @SwaggerApiSpec(summary = "일정 상세 조회", description = "일정 상세 정보를 조회합니다.", successMessage = "일정 상세 조회 성공", errors = {
                        ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.ENTITY_NOT_FOUND })
        @GetMapping("/{scheduleId}")
        public RestApiResponse<ScheduleResponseDTO> getSchedule(
                        @PathVariable Integer familyId,
                        @PathVariable String scheduleId) {
                return RestApiResponse.success(scheduleService.getSchedule(familyId, scheduleId));
        }

        @SwaggerApiSpec(summary = "일정 등록", description = "새로운 가족 일정을 등록합니다.", successMessage = "일정이 성공적으로 등록되었습니다.", errors = {
                        ErrorCode.INVALID_DATE_RANGE, ErrorCode.RESERVED_TITLE })
        @PostMapping
        public RestApiResponse<Void> createSchedule(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId,
                        @RequestBody ScheduleRequestDTO request) {
                scheduleService.createSchedule(userDetails.getId(), familyId, request);
                return RestApiResponse.success("일정이 성공적으로 등록되었습니다.");
        }

        @SwaggerApiSpec(summary = "일정 수정", description = "일정을 수정합니다. 가상 ID를 통해 개별 반복 일정만 수정할 수 있습니다.", successMessage = "일정이 수정되었습니다.", errors = {
                        ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INVALID_DATE_RANGE,
                        ErrorCode.RESERVED_TITLE })
        @PutMapping("/{scheduleId}")
        public RestApiResponse<Void> updateSchedule(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId,
                        @PathVariable String scheduleId,
                        @RequestBody ScheduleRequestDTO request) {
                scheduleService.updateSchedule(userDetails.getId(), familyId, scheduleId, request);
                return RestApiResponse.success("일정이 수정되었습니다.");
        }

        @SwaggerApiSpec(summary = "일정 삭제", description = "일정을 삭제합니다. delete_all=true일 경우 반복 일정 전체를 삭제합니다.", successMessage = "일정이 삭제되었습니다.", errors = {
                        ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.ENTITY_NOT_FOUND })
        @DeleteMapping("/{scheduleId}")
        public RestApiResponse<Void> deleteSchedule(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId,
                        @PathVariable String scheduleId,
                        @RequestParam(value = "delete_all", required = false, defaultValue = "false") boolean deleteAll) {
                scheduleService.deleteSchedule(userDetails.getId(), familyId, scheduleId, deleteAll);
                return RestApiResponse.success("일정이 삭제되었습니다.");
        }

        @SwaggerApiSpec(summary = "방문 상태 변경", description = "일정의 방문 완료 여부를 변경합니다.", successMessage = "방문 상태가 변경되었습니다.", errors = {
                        ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.ENTITY_NOT_FOUND })
        @PatchMapping("/{scheduleId}/visit")
        public RestApiResponse<Void> updateVisitStatus(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId,
                        @PathVariable String scheduleId,
                        @RequestParam boolean visited) {
                scheduleService.updateVisitStatus(userDetails.getId(), familyId, scheduleId, visited);
                return RestApiResponse.success("방문 상태가 변경되었습니다.");
        }
}