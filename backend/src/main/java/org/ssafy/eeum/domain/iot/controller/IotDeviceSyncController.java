package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.*;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.global.auth.model.DeviceDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;

import java.util.List;

/**
 * IoT 기기(Jetson 등)와 서버 간의 데이터 동기화를 담당하는 컨트롤러 클래스입니다.
 * 기기 초기화 정보, 앨범 및 목소리 데이터 동기화, 스트리밍 IP 업데이트 기능을 제공합니다.
 * 본 컨트롤러의 API는 기기 권한(ROLE_DEVICE)이 필요합니다.
 * 
 * @summary IoT 기기 동기화 컨트롤러
 */
@Tag(name = "IoT Device", description = "IoT 기기 전용 API (ROLE_DEVICE 필수)")
@RestController
@RequestMapping("/api/iot/device")
@RequiredArgsConstructor
public class IotDeviceSyncController {

        private final IotDeviceService iotDeviceService;
        private final IotSyncService iotSyncService;

        /**
         * 기기 부팅 시 필요한 초기 설정 및 가족 구성원 정보를 조회합니다.
         * 
         * @summary IoT 기기 초기화 데이터 조회
         * @param deviceDetails 인증된 기기 정보
         * @param serialNumber  기기 시리얼 번호
         * @return 초기화 정보 응답 DTO (기기 목록, 멤버 목록 포함)
         */
        @SwaggerApiSpec(summary = "IoT 초기화 데이터 조회", description = "기기 부팅 시 소속된 가족 그룹의 모든 기기 목록 및 정보를 조회합니다.", successMessage = "초기화 데이터 조회 성공")
        @GetMapping("/init")
        public RestApiResponse<IotDeviceSyncResponseDTO> initDevice(
                        @AuthenticationPrincipal DeviceDetails deviceDetails,
                        @RequestParam String serialNumber) {

                List<IotSimpleDeviceInfoResponseDTO> devices = iotDeviceService.getDevicesBySerialNumber(serialNumber);
                List<IotFamilyMemberDto> members = iotSyncService.getFamilyMembers(deviceDetails.getGroupId());

                return RestApiResponse.success(IotDeviceSyncResponseDTO.builder()
                                .status("success")
                                .serialNumber(serialNumber)
                                .groupId(deviceDetails.getGroupId())
                                .devices(devices)
                                .members(members)
                                .build());
        }

        /**
         * IoT 기기의 앨범(사진) 데이터 동기화를 위해 변경 내역 로그를 조회합니다.
         * 
         * @summary 앨범 데이터 동기화 조회
         * @param deviceDetails 인증된 기기 정보
         * @param lastLogId     마지막으로 동기화된 로그 ID
         * @return 앨범 동기화 데이터 DTO
         */
        @SwaggerApiSpec(summary = "[IoT] 앨범 동기화 데이터 조회", description = "IoT 기기가 아직 동기화하지 않은 최신 사진 변경 내역(추가/삭제)을 조회합니다.", successMessage = "앨범 동기화 데이터 조회 성공")
        @GetMapping("/sync/album")
        public RestApiResponse<IotSyncDto> syncAlbumForIot(
                        @AuthenticationPrincipal DeviceDetails deviceDetails,
                        @RequestParam(required = false, defaultValue = "0") Integer lastLogId) {
                IotSyncDto response = iotSyncService.getSyncData(deviceDetails.getGroupId(), "image", lastLogId);
                return RestApiResponse.success(response);
        }

        /**
         * IoT 기기의 현재 네트워크 IP를 기반으로 스트리밍 접속 URL 정보를 서버에 업데이트합니다.
         * 
         * @summary 스트리밍 IP 업데이트
         * @param familyId 가족 그룹 식별자
         * @param request  스트리밍 IP 요청 정보 DTO
         * @return 결과 없음
         */
        @SwaggerApiSpec(summary = "[IoT] 실시간 스트리밍 IP 업데이트", description = "IoT 기기의 현재 IP를 기반으로 스트리밍 접속 URL을 업데이트합니다.", successMessage = "스트리밍 IP 업데이트 성공")
        @PatchMapping("/{familyId}/streaming-ip")
        public RestApiResponse<Void> updateStreamingIp(
                        @PathVariable Integer familyId,
                        @RequestBody IotStreamingIpRequestDTO request) {
                iotDeviceService.updateStreamingIp(familyId, request);
                return RestApiResponse.success(null);
        }

        /**
         * IoT 기기의 목소리 메시지 데이터 동기화를 위해 변경 내역 로그를 조회합니다.
         * 
         * @summary 목소리 메시지 동기화 조회
         * @param deviceDetails 인증된 기기 정보
         * @param lastLogId     마지막으로 동기화된 로그 ID
         * @return 목소리 동기화 데이터 DTO
         */
        @SwaggerApiSpec(summary = "[IoT] 목소리 메시지 동기화 데이터 조회", description = "IoT 기기가 아직 동기화하지 않은 최신 목소리 메시지 변경 내역을 조회합니다.", successMessage = "목소리 동기화 데이터 조회 성공")
        @GetMapping("/sync/voice")
        public RestApiResponse<IotSyncDto> syncVoiceForIot(
                        @AuthenticationPrincipal DeviceDetails deviceDetails,
                        @RequestParam(required = false, defaultValue = "0") Integer lastLogId) {
                IotSyncDto response = iotSyncService.getSyncData(deviceDetails.getGroupId(), "voice", lastLogId);
                return RestApiResponse.success(response);
        }

        /**
         * 가족 구성원의 변경사항(이름, 프로필 사진 등)을 IoT 기기에 동기화하기 위해 최신 정보를 조회합니다.
         * 
         * @summary 가족 멤버 정보 동기화 조회
         * @param deviceDetails 인증된 기기 정보
         * @return 최신 가족 멤버 DTO 리스트
         */
        @SwaggerApiSpec(summary = "[IoT] 가족 멤버 정보 동기화", description = "가족 멤버의 이름, 프로필 이미지 등 최신 정보를 조회합니다.", successMessage = "멤버 정보 동기화 성공")
        @GetMapping("/sync/members")
        public RestApiResponse<List<IotFamilyMemberDto>> syncMembersForIot(
                        @AuthenticationPrincipal DeviceDetails deviceDetails) {
                List<IotFamilyMemberDto> response = iotSyncService.getFamilyMembers(deviceDetails.getGroupId());
                return RestApiResponse.success(response);
        }

}
