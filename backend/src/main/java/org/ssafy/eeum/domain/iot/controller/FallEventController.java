package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.eeum.domain.iot.dto.FallEventHistoryResponseDTO;
import org.ssafy.eeum.domain.iot.service.FallEventService;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * 낙상 이벤트 발생 이력 및 관련 영상 조회를 제공하는 컨트롤러 클래스입니다.
 * 특정 가족 그룹의 낙상 히스토리와 개별 이벤트의 녹화 영상 URL을 제공합니다.
 * 
 * @summary 낙상 이벤트 관리 컨트롤러
 */
@Tag(name = "Fall Event", description = "낙상 이벤트 관련 API")
@RestController
@RequestMapping("/api/falls")
@RequiredArgsConstructor
public class FallEventController {

    private final FallEventService fallEventService;

    /**
     * 특정 낙상 이벤트의 녹화 영상 URL을 조회합니다.
     * 영상 처리가 완료된 상태에서만 유효한 URL이 반환됩니다.
     * 
     * @summary 낙상 영상 URL 조회
     * @param eventId 낙상 이벤트 식별자
     * @return 영상 종류별 URL 맵 (전후 영상 등)
     */
    @SwaggerApiSpec(summary = "낙상 영상 조회", description = "낙상 이벤트의 전후 영상을 조회합니다. 영상 처리가 완료된(SUCCESS) 경우에만 URL을 반환합니다.", successMessage = "영상 URL 조회 성공", errors = {
            ErrorCode.ENTITY_NOT_FOUND, ErrorCode.VIDEO_NOT_READY })
    @GetMapping("/{eventId}/video")
    public RestApiResponse<Map<String, String>> getVideoUrl(@PathVariable Integer eventId) {
        Map<String, String> response = fallEventService.getVideoUrl(eventId);
        return RestApiResponse.success(HttpStatus.OK, "영상 URL 조회 성공", response);
    }

    /**
     * 특정 가족 그룹의 모든 낙상 이력 목록을 조회합니다.
     * 
     * @summary 가족별 낙상 이력 조회
     * @param familyId 가족 그룹 식별자
     * @return 낙상 이력 응답 DTO 리스트
     */
    @SwaggerApiSpec(summary = "낙상 이력 조회", description = "특정 가족 그룹의 모든 낙상 이력을 조회합니다. 영상 URL과 신뢰도 정보를 포함합니다.", successMessage = "낙상 이력 조회 성공")
    @GetMapping("/families/{familyId}")
    public RestApiResponse<List<FallEventHistoryResponseDTO>> getFallHistory(@PathVariable Integer familyId) {
        List<FallEventHistoryResponseDTO> response = fallEventService.getFallHistory(familyId);
        return RestApiResponse.success(HttpStatus.OK, "낙상 이력 조회 성공", response);
    }
}
