package org.ssafy.eeum.domain.health.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.health.dto.HealthConnectionRequestDTO;
import org.ssafy.eeum.domain.health.dto.HealthConnectionResponseDTO;
import org.ssafy.eeum.domain.health.dto.HealthMetricRequestDTO;
import org.ssafy.eeum.domain.health.service.HealthConnectionService;
import org.ssafy.eeum.domain.health.service.HealthService;
import org.ssafy.eeum.domain.health.dto.HealthReportResponseDTO;
import org.ssafy.eeum.domain.health.service.HealthReportService;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Health", description = "건강 데이터 관리 API")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

        private final HealthService healthService;
        private final HealthConnectionService healthConnectionService;
        private final HealthReportService healthReportService;

        @SwaggerApiSpec(summary = "일일 건강 리포트 조회", description = "특정 날짜의 건강 지표 요약 및 상세 분석 리포트를 조회합니다.", successMessage = "리포트 조회 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND })
        @GetMapping("/report")
        public RestApiResponse<HealthReportResponseDTO> getDailyReport(
                        @RequestParam Integer groupId,
                        @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {

                HealthReportResponseDTO response = healthReportService.getDailyReport(groupId, date);
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "건강 리포트 직접 분석 요청", description = "GMS를 호출하여 건강 데이터를 다시 분석하고 리포트를 생성합니다.", successMessage = "리포트 분석 및 생성 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND })
        @PostMapping("/analyze")
        public RestApiResponse<HealthReportResponseDTO> analyzeReport(
                        @RequestParam Integer groupId,
                        @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {

                HealthReportResponseDTO response = healthReportService.reanalyzeDailyReport(groupId, date);
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "최신 건강 지표 조회", description = "가족 그룹 내 피부양자의 가장 최신 건강 데이터를 조회합니다.", successMessage = "최신 건강 지표 조회 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND })
        @GetMapping("/latest")
        public RestApiResponse<org.ssafy.eeum.domain.health.entity.HealthMetric> getLatestMetrics(
                        @RequestParam Integer groupId) {
                org.ssafy.eeum.domain.health.entity.HealthMetric response = healthService
                                .getPatientLatestMetrics(groupId);
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "건강 데이터 업로드", description = "모바일에서 수집된 걸음 수, 심박수 등의 건강 데이터를 서버에 저장합니다.", successMessage = "건강 데이터 저장 및 기기 연동 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INVALID_INPUT_VALUE, ErrorCode.INTERNAL_SERVER_ERROR })
        @PostMapping("/data")
        public RestApiResponse<String> uploadMetrics(
                        @org.springframework.security.core.annotation.AuthenticationPrincipal org.ssafy.eeum.global.auth.model.CustomUserDetails userDetails,
                        @RequestParam Integer groupId,
                        @Valid @RequestBody List<HealthMetricRequestDTO> requests) {

                healthService.saveHealthMetrics(groupId, requests);
                return RestApiResponse.success("건강 데이터 저장 및 기기 연동 성공");
        }

        @SwaggerApiSpec(summary = "삼성 헬스 연동 상태 등록", description = "앱에서 확인된 삼성 헬스 연동 권한 상태를 서버에 등록합니다.", successMessage = "연동 상태 등록 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INVALID_INPUT_VALUE })
        @PostMapping("/connection")
        public RestApiResponse<HealthConnectionResponseDTO> registerConnection(
                        @RequestParam Integer groupId,
                        @Valid @RequestBody HealthConnectionRequestDTO request) {

                HealthConnectionResponseDTO response = healthConnectionService.registerConnection(groupId,
                                request);
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "삼성 헬스 연동 상태 조회", description = "현재 사용자의 삼성 헬스 연동 여부 및 마지막 동기화 시각을 조회합니다.", successMessage = "연동 상태 조회 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND })
        @GetMapping("/connection")
        public RestApiResponse<HealthConnectionResponseDTO> getConnectionStatus(
                        @RequestParam Integer groupId,
                        @RequestParam(defaultValue = "SAMSUNG_HEALTH") String provider) {

                HealthConnectionResponseDTO response = healthConnectionService.getConnectionStatus(groupId,
                                provider);
                return RestApiResponse.success(response);
        }
}
