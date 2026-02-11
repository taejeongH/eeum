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

/**
 * IoT 기기에서 발생하는 낙상 감지 이벤트를 처리하고 영상 업로드 절차를 지원하는 컨트롤러 클래스입니다.
 * 기기 인증(ROLE_DEVICE)이 필요하며, 업로드를 위한 Presigned URL 발급 및 감지 데이터 기록을 담당합니다.
 * 
 * @summary 기기용 낙상 감지 처리 컨트롤러
 */
@Tag(name = "IoT Fall", description = "IoT 낙상 감지 및 리포트 API (ROLE_DEVICE 필수)")
@RestController
@RequestMapping("/api/iot/device/falls")
@RequiredArgsConstructor
public class IotFallController {

    private final FallEventService fallEventService;

    /**
     * 낙상 감지 시 녹화 영상 업로드를 위한 S3 Presigned URL을 발급받습니다.
     * 
     * @summary 영상 업로드용 Presigned URL 발급
     * @param groupId 가족 그룹 식별자
     * @return 발급된 URL 및 파일 경로 정보 맵
     */
    @SwaggerApiSpec(summary = "낙상 이벤트 로그 URL 발급", description = "낙상 감지 시 영상 업로드를 위한 Presigned URL을 발급받습니다.", successMessage = "Presigned URL 발급 성공")
    @PostMapping("/presigned-url")
    public RestApiResponse<Map<String, String>> getPresignedUrl(
            @RequestParam Integer groupId) {
        Map<String, String> response = fallEventService.initiateFallLog(groupId);
        return RestApiResponse.success(HttpStatus.OK, "Presigned URL 발급 성공", response);
    }

    /**
     * IoT 기기에서 S3 영상 업로드를 완료한 후 서버에 최종 상태를 알립니다.
     * 
     * @summary 영상 업로드 완료 보고
     * @param request 영상 파일 경로 정보를 포함한 맵
     * @return 결과 없음
     */
    @SwaggerApiSpec(summary = "업로드 확인 완료", description = "IoT 기기에서 영상 업로드를 마치면 파일 경로를 서버에 알립니다.", successMessage = "업로드 확인 완료")
    @PostMapping("/upload-success")
    public RestApiResponse<Void> completeUpload(
            @RequestBody Map<String, String> request) {
        String videoPath = request.get("videoPath");
        fallEventService.completeFallLog(videoPath);
        return RestApiResponse.success(HttpStatus.OK, "업로드 확인 완료", null);
    }

    /**
     * IoT 기기로부터 낙상 감지 센서 데이터 및 분석 레벨 정보를 수신하여 처리합니다.
     * 
     * @summary 낙상 감지 데이터 수신 및 처리
     * @param deviceDetails 인증된 기기 정보
     * @param request       낙상 감지 상세 데이터 DTO
     * @return 처리 결과 및 후속 조치 정보 (업로드 필요 시 URL 등)
     */
    @SwaggerApiSpec(summary = "낙상 감지 데이터 전송", description = "라즈베리파이 등 IoT 기기에서 낙상 감지 시 호출하는 API입니다. 레벨 1일 때 Presigned URL을 반환합니다.", successMessage = "낙상 감지 데이터 처리 완료", errors = {
            ErrorCode.IOT_DEVICE_NOT_FOUND })
    @PostMapping("/detection")
    public RestApiResponse<Map<String, String>> detectFall(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @RequestBody FallDetectionRequestDTO request) {
        String serialNumber = request.getDeviceId();
        Integer groupId = deviceDetails.getGroupId();
        Map<String, String> response = fallEventService.handleFallDetection(serialNumber, request, groupId);
        return RestApiResponse.success(HttpStatus.OK, "낙상 감지 데이터 처리 완료", response);
    }

}
