package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotDeviceInitResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotDevicePairRequestDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceRefreshRequestDTO;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

@Tag(name = "IoT Auth", description = "IoT 기기 인증 API (토큰 불필요)")
@RestController
@RequestMapping("/api/iot/auth")
@RequiredArgsConstructor
public class IotAuthController {

        private final IotDeviceService iotDeviceService;

        @SwaggerApiSpec(summary = "IoT 페어링", description = "페어링 코드를 사용하여 기기를 가족 그룹에 등록합니다.", successMessage = "페어링 성공", errors = {
                        ErrorCode.IOT_INVALID_PAIRING_CODE, ErrorCode.FAMILY_NOT_FOUND,
                        ErrorCode.IOT_UNREGISTERED_SERIAL_NUMBER })
        @PostMapping("/pairing")
        public RestApiResponse<IotDeviceInitResponseDTO> pairDevice(@RequestBody IotDevicePairRequestDTO request) {
                return RestApiResponse.success(iotDeviceService.pairDevice(request));
        }

        @SwaggerApiSpec(summary = "IoT 토큰 갱신", description = "리프레시 토큰과 디바이스 시크릿을 사용하여 액세스 토큰을 갱신합니다.", successMessage = "토큰 갱신 성공", errors = {
                        ErrorCode.INVALID_TOKEN })
        @PostMapping("/refresh")
        public RestApiResponse<IotDeviceInitResponseDTO> refreshTokens(
                        @RequestBody IotDeviceRefreshRequestDTO request) {
                return RestApiResponse.success(iotDeviceService.refreshDeviceTokens(
                                request.getSerialNumber(),
                                request.getRefreshToken()));
        }

}
