package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.eeum.domain.iot.service.FallEventService;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.Map;

@Tag(name = "Fall Event", description = "낙상 이벤트 관련 API")
@RestController
@RequestMapping("/api/falls")
@RequiredArgsConstructor
public class FallEventController {

    private final FallEventService fallEventService;

    @SwaggerApiSpec(summary = "낙상 영상 조회", description = "낙상 이벤트의 전후 영상을 조회합니다. 영상 처리가 완료된(SUCCESS) 경우에만 URL을 반환합니다.", successMessage = "영상 URL 조회 성공", errors = {
            ErrorCode.ENTITY_NOT_FOUND, ErrorCode.VIDEO_NOT_READY })
    @GetMapping("/{eventId}/video")
    public RestApiResponse<Map<String, String>> getVideoUrl(@PathVariable Integer eventId) {
        Map<String, String> response = fallEventService.getVideoUrl(eventId);
        return RestApiResponse.success(HttpStatus.OK, "영상 URL 조회 성공", response);
    }
}
