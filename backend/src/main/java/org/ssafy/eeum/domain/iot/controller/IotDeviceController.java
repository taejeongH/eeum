package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotDeviceRequestDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceUpdateRequestDTO;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Tag(name = "IoT Device", description = "IoT 기기 관리 API")
@RestController
@RequestMapping("/api/families/{familyId}/devices")
@RequiredArgsConstructor
public class IotDeviceController {

    private final IotDeviceService iotDeviceService;

    @SwaggerApiSpec(summary = "IoT 기기 등록", description = "새로운 IoT 기기를 등록합니다.", successMessage = "기기 등록 성공", errors = {
            ErrorCode.FAMILY_NOT_FOUND })
    @PostMapping
    public RestApiResponse<Integer> registerDevice(
            @PathVariable Integer familyId,
            @RequestBody IotDeviceRequestDTO request) {
        Integer deviceId = iotDeviceService.registerDevice(familyId, request);
        return RestApiResponse.success(deviceId);
    }

    @SwaggerApiSpec(summary = "IoT 기기 목록 조회", description = "특정 그룹의 등록된 기기 목록을 조회합니다.", successMessage = "기기 목록 조회 성공")
    @GetMapping
    public RestApiResponse<List<IotDeviceResponseDTO>> getDevices(@PathVariable Integer familyId) {
        return RestApiResponse.success(iotDeviceService.getDevicesByGroup(familyId));
    }

    @SwaggerApiSpec(summary = "IoT 기기 정보 수정", description = "기기의 별칭이나 위치 정보를 수정합니다.", successMessage = "기기 정보 수정 성공", errors = {
            ErrorCode.IOT_DEVICE_NOT_FOUND })
    @PatchMapping("/{deviceId}")
    public RestApiResponse<Void> updateDevice(
            @PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer deviceId,
            @RequestBody IotDeviceUpdateRequestDTO updateDto) {
        iotDeviceService.updateDevice(userDetails, deviceId, updateDto);
        return RestApiResponse.success("기기 정보가 수정되었습니다.");
    }

    @SwaggerApiSpec(summary = "IoT 기기 삭제", description = "기기를 삭제(Soft Delete)합니다.", successMessage = "기기 삭제 성공", errors = {
            ErrorCode.IOT_DEVICE_NOT_FOUND })
    @DeleteMapping("/{deviceId}")
    public RestApiResponse<Void> deleteDevice(
            @PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer deviceId) {
        iotDeviceService.deleteDevice(userDetails, deviceId);
        return RestApiResponse.success("기기가 삭제되었습니다.");
    }
}
