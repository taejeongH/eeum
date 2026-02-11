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

/**
 * 가족 구성원들의 일정을 관리하는 컨트롤러 클래스입니다.
 * 일정 조회, 등록, 수정, 삭제 및 방문 상태 관리 API를 제공합니다.
 * 
 * @summary 가족 일정 관리 컨트롤러
 */
@Tag(name = "Schedule", description = "가족 일정 관리 API")
@RestController
@RequestMapping("/api/families/{familyId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {
        private final ScheduleService scheduleService;

        /**
         * 특정 연도와 월의 모든 가족 일정을 조회합니다.
         * 카테고리, 키워드, 대상자 이름, 방문 여부 등 다양한 필터 조건을 지원합니다.
         * 
         * @summary 월간 일정 필터링 조회
         * @param userDetails  인증된 사용자 정보
         * @param familyId     가족 그룹 식별자
         * @param year         조회 연도
         * @param month        조회 월
         * @param category     일정 카테고리 (선택)
         * @param keyword      검색 키워드 (선택)
         * @param targetPerson 일정 대상자 (선택)
         * @param isVisited    방문 완료 여부 (선택)
         * @return 일정 응답 DTO 리스트
         */
        @SwaggerApiSpec(summary = "월간 일정 조회", description = "특정 연도/월의 모든 가족 일정을 조회합니다. 카테고리, 키워드, 대상자, 방문 여부로 필터링할 수 있습니다.", successMessage = "월간 일정 조회 성공")
        @GetMapping
        public RestApiResponse<List<ScheduleResponseDTO>> getMonthlySchedules(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId,
                        @RequestParam int year,
                        @RequestParam int month,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String targetPerson,
                        @RequestParam(required = false) Boolean isVisited) {
                return RestApiResponse
                                .success(scheduleService.getMonthlySchedules(userDetails.getId(), familyId, year, month,
                                                category, keyword,
                                                targetPerson,
                                                isVisited));
        }

        /**
         * 특정 일정의 상세 정보를 조회합니다.
         * 반복 일정의 경우 가상 ID(부모ID_날짜)를 통해 개별 발생 정보를 조회할 수 있습니다.
         * 
         * @summary 일정 상세 정보 조회
         * @param userDetails 인증된 사용자 정보
         * @param familyId    가족 그룹 식별자
         * @param scheduleId  일정 식별자 (DB 식별자 또는 가상 ID)
         * @return 일정 상세 정보 DTO
         */
        @SwaggerApiSpec(summary = "일정 상세 조회", description = "일정 상세 정보를 조회합니다.", successMessage = "일정 상세 조회 성공", errors = {
                        ErrorCode.SCHEDULE_NOT_FOUND, ErrorCode.ENTITY_NOT_FOUND })
        @GetMapping("/{scheduleId}")
        public RestApiResponse<ScheduleResponseDTO> getSchedule(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId,
                        @PathVariable String scheduleId) {
                return RestApiResponse.success(scheduleService.getSchedule(userDetails.getId(), familyId, scheduleId));
        }

        /**
         * 새로운家族 일정을 등록합니다.
         * 제목, 기간, 카테고리, 반복 설정 등을 포함할 수 있습니다.
         * 
         * @summary 신규 일정 등록
         * @param userDetails 인증된 사용자 정보
         * @param familyId    가족 그룹 식별자
         * @param request     일정 등록 요청 DTO
         * @return 성공 메시지
         */
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

        /**
         * 기존 일정을 수정합니다.
         * 반복 일정의 경우 특정 날짜의 일정만 수정하거나 전체 일정을 관리할 수 있습니다.
         * 
         * @summary 일정 정보 수정
         * @param userDetails 인증된 사용자 정보
         * @param familyId    가족 그룹 식별자
         * @param scheduleId  일정 식별자
         * @param request     일정 수정 요청 DTO
         * @return 성공 메시지
         */
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

        /**
         * 일정을 삭제합니다.
         * 단일 일정 삭제 또는 반복 일정 전체 삭제를 선택할 수 있습니다.
         * 
         * @summary 일정 삭제
         * @param userDetails 인증된 사용자 정보
         * @param familyId    가족 그룹 식별자
         * @param scheduleId  일정 식별자
         * @param deleteAll   반복 일정인 경우 연관된 모든 일정을 삭제할지 여부
         * @return 성공 메시지
         */
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

        /**
         * 일정의 방문 상태(완료 여부)를 업데이트합니다.
         * 주로 병원 방문이나 특정 외출 일정의 이행 여부를 관리하는 데 사용됩니다.
         * 
         * @summary 방문 완료 상태 업데이트
         * @param userDetails 인증된 사용자 정보
         * @param familyId    가족 그룹 식별자
         * @param scheduleId  일정 식별자
         * @param visited     방문 완료 여부
         * @return 성공 메시지
         */
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
