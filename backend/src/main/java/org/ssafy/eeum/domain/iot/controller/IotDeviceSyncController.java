package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotSyncDto;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.domain.iot.dto.IotDeviceSyncResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotSimpleDeviceInfoResponseDTO;
import org.ssafy.eeum.global.auth.model.DeviceDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;

import java.util.List;

@Tag(name = "IoT Device", description = "IoT 기기 전용 API (ROLE_DEVICE 필수)")
@RestController
@RequestMapping("/api/iot/device")
@RequiredArgsConstructor
public class IotDeviceSyncController {

        private final IotDeviceService iotDeviceService;
        private final IotSyncService iotSyncService;

        @SwaggerApiSpec(summary = "IoT 초기화 데이터 조회", description = "기기 부팅 시 소속된 가족 그룹의 모든 기기 목록 및 정보를 조회합니다.", successMessage = "초기화 데이터 조회 성공")
        @GetMapping("/init")
        public RestApiResponse<IotDeviceSyncResponseDTO> initDevice(
                        @AuthenticationPrincipal DeviceDetails deviceDetails,
                        @RequestParam String serialNumber) {

                // 인증된 기기의 그룹 정보를 기반으로 목록 조회
                List<IotSimpleDeviceInfoResponseDTO> devices = iotDeviceService.getDevicesBySerialNumber(serialNumber);

                return RestApiResponse.success(IotDeviceSyncResponseDTO.builder()
                                .status("success")
                                .serialNumber(serialNumber)
                                .groupId(deviceDetails.getGroupId())
                                .devices(devices)
                                .build());
        }

        @SwaggerApiSpec(summary = "[IoT] 앨범 동기화 데이터 조회", description = "IoT 기기가 아직 동기화하지 않은 최신 사진 변경 내역(추가/삭제)을 조회합니다.", successMessage = "앨범 동기화 데이터 조회 성공")
        @GetMapping("/sync/album")
        public RestApiResponse<IotSyncDto> syncAlbumForIot(
                        @AuthenticationPrincipal DeviceDetails deviceDetails,
                        @RequestParam(required = false, defaultValue = "0") Integer lastLogId) {
                // 기기 인증 정보에서 Group ID 추출 (토큰 subject가 GROUP:ID 형식이므로 serialNumber는 실제 시리얼이 아님)
                IotSyncDto response = iotSyncService.getSyncData(deviceDetails.getGroupId(), "image", lastLogId);
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "[IoT] 목소리 메시지 동기화 데이터 조회", description = "IoT 기기가 아직 동기화하지 않은 최신 목소리 메시지 변경 내역을 조회합니다.", successMessage = "목소리 동기화 데이터 조회 성공")
        @GetMapping("/sync/voice")
        public RestApiResponse<IotSyncDto> syncVoiceForIot(
                        @AuthenticationPrincipal DeviceDetails deviceDetails,
                        @RequestParam(required = false, defaultValue = "0") Integer lastLogId) {
                IotSyncDto response = iotSyncService.getSyncData(deviceDetails.getGroupId(), "voice", lastLogId);
                return RestApiResponse.success(response);
        }
}
