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

/**
 * 모바일 앱 사용자용 실시간 알림 센터 API를 제공하는 컨트롤러 클래스입니다.
 * 가족 그룹의 알림 이력 조회, 읽음 상태 관리 및 이력 삭제 기능을 담당합니다.
 * 
 * @summary 모바일 사용자용 알림 센터 컨트롤러
 */
@Tag(name = "IoT Notification Center", description = "IoT 알림 센터 및 이력 관리 API")
@RestController
@RequestMapping("/api/families/{familyId}/notifications")
@RequiredArgsConstructor
public class IotNotificationController {

    private final IotNotificationService notificationService;

    /**
     * 특정 가족 그룹의 전체 알림 이력을 페이지 단위로 조회합니다.
     * 
     * @summary 알림 목록 조회
     * @param familyId   가족 그룹 식별자
     * @param unreadOnly 안읽은 알림만 필터링 여부
     * @param pageable   페이지네이션 정보
     * @return 알림 정보 DTO 페이지
     */
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

    /**
     * 해당 가족 그룹의 모든 안읽은 알림을 읽음 상태로 일괄 변경합니다.
     * 
     * @summary 전체 알림 읽음 처리
     * @param familyId 가족 그룹 식별자
     * @return 결과 없음
     */
    @Operation(summary = "전체 읽음 처리", description = "해당 그룹의 모든 안읽은 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all")
    public RestApiResponse<Void> markAllAsRead(@PathVariable Integer familyId) {
        notificationService.markAllAsRead(familyId);
        return RestApiResponse.success(null);
    }

    /**
     * 특정 알림 이력을 삭제합니다.
     * 
     * @summary 알림 이력 단건 삭제
     * @param familyId 가족 그룹 식별자
     * @param id       삭제 대상 알림 식별자
     * @return 결과 없음
     */
    @Operation(summary = "단건 삭제", description = "특정 알림 이력을 삭제합니다.")
    @DeleteMapping("/{id}")
    public RestApiResponse<Void> deleteNotification(
            @PathVariable Integer familyId,
            @PathVariable Long id) {
        notificationService.deleteNotification(id, familyId);
        return RestApiResponse.success(null);
    }

    /**
     * 읽음 처리된 모든 알림 이력을 일괄 삭제합니다.
     * 
     * @summary 읽은 알림 전체 삭제
     * @param familyId 가족 그룹 식별자
     * @return 결과 없음
     */
    @Operation(summary = "읽은 알림 전체 삭제", description = "읽음 상태인 모든 알림을 삭제합니다.")
    @DeleteMapping("/read-all")
    public RestApiResponse<Void> deleteAllRead(@PathVariable Integer familyId) {
        notificationService.deleteAllRead(familyId);
        return RestApiResponse.success(null);
    }
}
