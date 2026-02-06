package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotNotificationResponseDTO;
import org.ssafy.eeum.domain.iot.service.IotNotificationService;
import org.ssafy.eeum.global.auth.model.DeviceDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;

@Tag(name = "IoT Device Notification", description = "IoT 기기 전용 알림 관리 API")
@RestController
@RequestMapping("/api/iot/device/notifications")
@RequiredArgsConstructor
public class IotDeviceNotificationController {

    private final IotNotificationService notificationService;

    @Operation(summary = "기기용 알림 목록 조회", description = "기기가 속한 그룹의 알림 이력을 조회합니다.")
    @GetMapping
    public RestApiResponse<Page<IotNotificationResponseDTO>> getDeviceNotifications(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {

        Integer familyId = deviceDetails.getGroupId();
        Page<IotNotificationResponseDTO> notifications = notificationService
                .getNotifications(familyId, unreadOnly, pageable)
                .map(IotNotificationResponseDTO::from);

        return RestApiResponse.success(notifications);
    }

    @Operation(summary = "기기용 알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다 (REST 기반 ACK).")
    @PatchMapping("/{messageId}/read")
    public RestApiResponse<Void> markAsRead(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @PathVariable String messageId) {

        notificationService.markAsRead(messageId);
        return RestApiResponse.success(null);
    }

    @Operation(summary = "기기용 전체 읽음 처리", description = "본인 그룹의 모든 안읽은 알림을 읽음 처리합니다.")
    @PatchMapping("/read-all")
    public RestApiResponse<Void> markAllAsRead(@AuthenticationPrincipal DeviceDetails deviceDetails) {
        Integer familyId = deviceDetails.getGroupId();
        notificationService.markAllAsRead(familyId);
        return RestApiResponse.success(null);
    }

    @Operation(summary = "기기용 알림 삭제", description = "특정 알림 이력을 삭제합니다.")
    @DeleteMapping("/{id}")
    public RestApiResponse<Void> deleteNotification(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @PathVariable Long id) {
        Integer familyId = deviceDetails.getGroupId();
        notificationService.deleteNotification(id, familyId);
        return RestApiResponse.success(null);
    }

    @Operation(summary = "기기용 읽은 알림 전체 삭제", description = "읽음 상태인 모든 알림을 삭제합니다.")
    @DeleteMapping("/read-all")
    public RestApiResponse<Void> deleteAllRead(@AuthenticationPrincipal DeviceDetails deviceDetails) {
        Integer familyId = deviceDetails.getGroupId();
        notificationService.deleteAllRead(familyId);
        return RestApiResponse.success(null);
    }
}
