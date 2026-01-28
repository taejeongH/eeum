package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotDeviceRequestDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceUpdateDTO;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.global.common.response.RestApiResponse;

import java.util.List;

@Tag(name = "IoT Device", description = "IoT 기기 관리 API")
@RestController
@RequestMapping("/api/iot/devices")
@RequiredArgsConstructor
public class IotDeviceController {

    private final IotDeviceService iotDeviceService;

    @Operation(summary = "IoT 기기 등록", description = "새로운 IoT 기기를 등록합니다.")
    @PostMapping
    public RestApiResponse<Integer> registerDevice(@RequestBody IotDeviceRequestDTO request) {
        Integer deviceId = iotDeviceService.registerDevice(request);
        return RestApiResponse.success(deviceId);
    }

    @Operation(summary = "IoT 기기 목록 조회", description = "특정 그룹의 등록된 기기 목록을 조회합니다.")
    @GetMapping
    public RestApiResponse<List<IotDeviceResponseDTO>> getDevices(@RequestParam Integer groupId) {
        return RestApiResponse.success(iotDeviceService.getDevicesByGroup(groupId));
    }

    @Operation(summary = "IoT 기기 정보 수정", description = "기기의 별칭이나 위치 정보를 수정합니다.")
    @PatchMapping("/{deviceId}")
    public RestApiResponse<Void> updateDevice(@PathVariable Integer deviceId,
            @RequestBody IotDeviceUpdateDTO updateDto) {
        iotDeviceService.updateDevice(deviceId, updateDto);
        return RestApiResponse.success(null);
    }

    @Operation(summary = "IoT 기기 삭제", description = "기기를 삭제(Soft Delete)합니다.")
    @DeleteMapping("/{deviceId}")
    public RestApiResponse<Void> deleteDevice(@PathVariable Integer deviceId) {
        iotDeviceService.deleteDevice(deviceId);
        return RestApiResponse.success(null);
    }
}
