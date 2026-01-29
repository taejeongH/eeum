package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.service.FallEventService;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.auth.model.DeviceDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.ssafy.eeum.domain.iot.dto.FallDetectionRequestDTO;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Tag(name = "IoT Fall", description = "IoT 낙상 감지 및 리포트 API (ROLE_DEVICE 필수)")
@RestController
@RequestMapping("/api/iot/device/falls")
@RequiredArgsConstructor
public class IotFallController {

    private final FallEventService fallEventService;

    @SwaggerApiSpec(summary = "낙상 이벤트 로그 URL 발급", description = "낙상 감지 시 영상 업로드를 위한 Presigned URL을 발급받습니다.", successMessage = "Presigned URL 발급 성공")
    @PostMapping("/presigned-url")
    public RestApiResponse<Map<String, String>> getPresignedUrl(
            @RequestParam Integer groupId) {
        Map<String, String> response = fallEventService.initiateFallLog(groupId);
        return RestApiResponse.success(HttpStatus.OK, "Presigned URL 발급 성공", response);
    }

    @SwaggerApiSpec(summary = "업로드 확인 완료", description = "IoT 기기에서 영상 업로드를 마치면 파일 경로를 서버에 알립니다.", successMessage = "업로드 확인 완료")
    @PostMapping("/upload-success")
    public RestApiResponse<Void> completeUpload(
            @RequestBody Map<String, String> request) {
        String videoPath = request.get("videoPath");
        fallEventService.completeFallLog(videoPath);
        return RestApiResponse.success(HttpStatus.OK, "업로드 확인 완료", null);
    }

    @SwaggerApiSpec(summary = "낙상 감지 데이터 전송", description = "라즈베리파이 등 IoT 기기에서 낙상 감지 시 호출하는 API입니다. 레벨 1일 때 Presigned URL을 반환합니다.", successMessage = "낙상 감지 데이터 처리 완료", errors = {
            ErrorCode.IOT_DEVICE_NOT_FOUND })
    @PostMapping("/detection")
    public RestApiResponse<Map<String, String>> detectFall(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @RequestBody FallDetectionRequestDTO request) {

        String serialNumber = deviceDetails.getSerialNumber();
        Map<String, String> response = fallEventService.handleFallDetection(serialNumber, request);

        return RestApiResponse.success(HttpStatus.OK, "낙상 감지 데이터 처리 완료", response);
    }

}
