package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.service.FallEventService;
import org.ssafy.eeum.global.common.response.RestApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/iot/falls")
@RequiredArgsConstructor
public class IotFallController {

    private final FallEventService fallEventService;

    @Operation(summary = "낙상 이벤트 로그 URL 발급", description = "낙상 감지 시 영상 업로드를 위한 Presigned URL을 발급받습니다.")
    @PostMapping("/presigned-url")
    public RestApiResponse<Map<String, String>> getPresignedUrl(
            @RequestParam Long groupId) {
        Map<String, String> response = fallEventService.initiateFallLog(groupId);
        return RestApiResponse.success(org.springframework.http.HttpStatus.OK, "Presigned URL 발급 성공", response);
    }

    @Operation(summary = "낙상 영상 업로드 완료", description = "IoT 기기에서 영상 업로드를 마치면 파일 경로를 서버에 알립니다.")
    @PostMapping("/upload-success")
    public RestApiResponse<Void> completeUpload(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> request) {
        String videoPath = request.get("videoPath");
        fallEventService.completeFallLog(videoPath);
        return RestApiResponse.success(org.springframework.http.HttpStatus.OK, "업로드 확인 완료", null);
    }

}
