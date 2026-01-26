package org.ssafy.eeum.domain.health.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.health.dto.HealthConnectionRequestDTO;
import org.ssafy.eeum.domain.health.dto.HealthConnectionResponseDTO;
import org.ssafy.eeum.domain.health.dto.HealthMetricRequestDTO;
import org.ssafy.eeum.domain.health.service.HealthConnectionService;
import org.ssafy.eeum.domain.health.service.HealthService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Tag(name = "Health", description = "건강 데이터 관리 API")
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

        private final HealthService healthService;
        private final HealthConnectionService healthConnectionService;

        @SwaggerApiSpec(summary = "건강 데이터 업로드", description = "모바일에서 수집된 걸음 수, 심박수 등의 건강 데이터를 서버에 저장합니다.", successMessage = "건강 데이터 저장 및 기기 연동 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INVALID_INPUT_VALUE, ErrorCode.INTERNAL_SERVER_ERROR })
        @PostMapping("/data")
        public RestApiResponse<String> uploadMetrics(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @Valid @RequestBody List<HealthMetricRequestDTO> requests) {

                healthService.saveHealthMetrics(userDetails.getId(), requests);
                return RestApiResponse.success("건강 데이터 저장 및 기기 연동 성공");
        }

        @SwaggerApiSpec(summary = "삼성 헬스 연동 상태 등록", description = "앱에서 확인된 삼성 헬스 연동 권한 상태를 서버에 등록합니다.", successMessage = "연동 상태 등록 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INVALID_INPUT_VALUE })
        @PostMapping("/connection")
        public RestApiResponse<HealthConnectionResponseDTO> registerConnection(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @Valid @RequestBody HealthConnectionRequestDTO request) {

                HealthConnectionResponseDTO response = healthConnectionService.registerConnection(userDetails.getId(),
                                request);
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "삼성 헬스 연동 상태 조회", description = "현재 사용자의 삼성 헬스 연동 여부 및 마지막 동기화 시각을 조회합니다.", successMessage = "연동 상태 조회 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND })
        @GetMapping("/connection")
        public RestApiResponse<HealthConnectionResponseDTO> getConnectionStatus(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "SAMSUNG_HEALTH") String provider) {

                HealthConnectionResponseDTO response = healthConnectionService.getConnectionStatus(userDetails.getId(),
                                provider);
                return RestApiResponse.success(response);
        }
}
