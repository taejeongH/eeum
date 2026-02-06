package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotNotificationResponseDTO;
import org.ssafy.eeum.domain.iot.service.IotNotificationService;
import org.ssafy.eeum.global.common.response.RestApiResponse;

@Tag(name = "IoT Notification Center", description = "IoT 알림 센터 및 이력 관리 API")
@RestController
@RequestMapping("/api/families/{familyId}/notifications")
@RequiredArgsConstructor
public class IotNotificationController {

    private final IotNotificationService notificationService;

    @Operation(summary = "알림 목록 조회 (페이지네이션)", description = "가족 그룹의 알림 이력을 조회합니다. unreadOnly 파라미터로 안읽은 알림만 필터링 가능합니다.")
    @GetMapping
    public RestApiResponse<Page<IotNotificationResponseDTO>> getNotifications(
            @PathVariable Integer familyId,
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<IotNotificationResponseDTO> notifications = notificationService
                .getNotifications(familyId, unreadOnly, pageable)
                .map(IotNotificationResponseDTO::from);

        return RestApiResponse.success(notifications);
    }

    @Operation(summary = "전체 읽음 처리", description = "해당 그룹의 모든 안읽은 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all")
    public RestApiResponse<Void> markAllAsRead(@PathVariable Integer familyId) {
        notificationService.markAllAsRead(familyId);
        return RestApiResponse.success(null);
    }

    @Operation(summary = "단건 삭제", description = "특정 알림 이력을 삭제합니다.")
    @DeleteMapping("/{id}")
    public RestApiResponse<Void> deleteNotification(
            @PathVariable Integer familyId,
            @PathVariable Long id) {
        notificationService.deleteNotification(id, familyId);
        return RestApiResponse.success(null);
    }

    @Operation(summary = "읽은 알림 전체 삭제", description = "읽음 상태인 모든 알림을 삭제합니다.")
    @DeleteMapping("/read-all")
    public RestApiResponse<Void> deleteAllRead(@PathVariable Integer familyId) {
        notificationService.deleteAllRead(familyId);
        return RestApiResponse.success(null);
    }
}
