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

/**
 * IoT 기기(Jetson 등)에서 직접 접근하는 실시간 알림 관리 API를 제공하는 컨트롤러 클래스입니다.
 * 기기가 설치된 가족 그룹의 알림 목록 조회, 읽음 처리 및 삭제 기능을 담당합니다.
 * 
 * @summary 기기용 실시간 알림 관리 컨트롤러
 */
@Tag(name = "IoT Device Notification", description = "IoT 기기 전용 알림 관리 API")
@RestController
@RequestMapping("/api/iot/device/notifications")
@RequiredArgsConstructor
public class IotDeviceNotificationController {

    private final IotNotificationService notificationService;

    /**
     * 기기가 소속된 가족 그룹의 알림 이력을 페이지 단위로 조회합니다.
     * 
     * @summary 기기용 알림 목록 조회
     * @param deviceDetails 인증된 기기 정보
     * @param unreadOnly    안읽은 알림만 필터링 여부
     * @param pageable      페이지네이션 정보
     * @return 알림 정보 DTO 페이지
     */
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

    /**
     * 특정 알림 메시지를 읽음 상태로 변경합니다 (ACK 처리).
     * 
     * @summary 기기용 알림 읽음 처리
     * @param deviceDetails 인증된 기기 정보
     * @param messageId     알림 메시지 식별자 (MongoDB ID 등)
     * @return 결과 없음
     */
    @Operation(summary = "기기용 알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다 (REST 기반 ACK).")
    @PatchMapping("/{messageId}/read")
    public RestApiResponse<Void> markAsRead(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @PathVariable String messageId) {

        notificationService.markAsRead(messageId);
        return RestApiResponse.success(null);
    }

    /**
     * 해당 가족 그룹의 모든 안읽은 알림을 일괄 읽음 처리합니다.
     * 
     * @summary 기기용 전체 알림 읽음 처리
     * @param deviceDetails 인증된 기기 정보
     * @return 결과 없음
     */
    @Operation(summary = "기기용 전체 읽음 처리", description = "본인 그룹의 모든 안읽은 알림을 읽음 처리합니다.")
    @PatchMapping("/read-all")
    public RestApiResponse<Void> markAllAsRead(@AuthenticationPrincipal DeviceDetails deviceDetails) {
        Integer familyId = deviceDetails.getGroupId();
        notificationService.markAllAsRead(familyId);
        return RestApiResponse.success(null);
    }

    /**
     * 특정 알림 이력을 영구 삭제합니다.
     * 
     * @summary 기기용 알림 삭제
     * @param deviceDetails 인증된 기기 정보
     * @param id            삭제 대상 알림 식별자
     * @return 결과 없음
     */
    @Operation(summary = "기기용 알림 삭제", description = "특정 알림 이력을 삭제합니다.")
    @DeleteMapping("/{id}")
    public RestApiResponse<Void> deleteNotification(
            @AuthenticationPrincipal DeviceDetails deviceDetails,
            @PathVariable Long id) {
        Integer familyId = deviceDetails.getGroupId();
        notificationService.deleteNotification(id, familyId);
        return RestApiResponse.success(null);
    }

    /**
     * 읽음 상태인 모든 알림 이력을 일괄 삭제합니다.
     * 
     * @summary 기기용 읽은 알림 전체 삭제
     * @param deviceDetails 인증된 기기 정보
     * @return 결과 없음
     */
    @Operation(summary = "기기용 읽은 알림 전체 삭제", description = "읽음 상태인 모든 알림을 삭제합니다.")
    @DeleteMapping("/read-all")
    public RestApiResponse<Void> deleteAllRead(@AuthenticationPrincipal DeviceDetails deviceDetails) {
        Integer familyId = deviceDetails.getGroupId();
        notificationService.deleteAllRead(familyId);
        return RestApiResponse.success(null);
    }
}
