package org.ssafy.eeum.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.eeum.domain.notification.dto.NotificationHistoryResponseDto;
import org.ssafy.eeum.domain.notification.dto.NotificationTestRequestDto;
import org.ssafy.eeum.domain.notification.service.NotificationService;

import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Tag(name = "notifications", description = "알림 관리")
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/notifications")
public class NotificationController {

    private final org.ssafy.eeum.domain.notification.service.NotificationService notificationService;
    private final org.ssafy.eeum.domain.notification.service.FallDetectionService fallDetectionService;
    private final org.ssafy.eeum.domain.notification.service.IotEventService iotEventService;

    @Operation(summary = "알림 생성 및 전송 테스트", description = "알림을 생성하고(DB 저장) 지정된 유저에게 전송합니다.")
    @PostMapping("/test")
    public ResponseEntity<String> createAndSendNotification(@RequestBody NotificationTestRequestDto requestDto) {
        // 1. 알림 생성 (DB 저장)
        Long notificationId = notificationService.createNotification(
                requestDto.getFamilyId(),
                requestDto.getTitle(),
                requestDto.getMessage(),
                requestDto.getType()
        );

        // 2. 알림 전송 (FCM)
        notificationService.sendNotification(notificationId, requestDto.getTargetUserId());

        return ResponseEntity.ok("알림이 생성되고 전송되었습니다 (ID: " + notificationId + ")");
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 유저가 특정 알림을 읽었음을 표시합니다.")
    @PostMapping("/read")
    public ResponseEntity<String> markAsRead(@RequestBody org.ssafy.eeum.domain.notification.dto.NotificationReadRequestDto requestDto) {
        log.info("Received markAsRead request: NotificationID={}, UserID={}", requestDto.getNotificationId(), requestDto.getUserId());
        notificationService.markAsRead(requestDto.getNotificationId(), requestDto.getUserId());
        log.info("markAsRead completed for NotificationID={}", requestDto.getNotificationId());
        return ResponseEntity.ok("알림이 읽음 처리되었습니다.");
    }

    @Operation(summary = "낙상 감지 테스트", description = "낙상 이벤트를 발생시켜 우선순위에 따른 순차 발송을 테스트합니다.")
    @PostMapping("/fall-test/{familyId}")
    public ResponseEntity<String> triggerFallDetection(@org.springframework.web.bind.annotation.PathVariable Integer familyId) {
        fallDetectionService.handleFallDetection(familyId, "테스트 낙상 감지 발생!");
        return ResponseEntity.ok("낙상 감지가 발생했습니다. 서버 로그와 알림을 확인하세요.");
    }

    @Operation(summary = "IoT 이벤트 테스트 (외출/귀가)", description = "IoT 이벤트(OUTING/RETURN)를 발생시켜 보호자 전원에게 알림을 보냅니다.")
    @PostMapping("/iot-test/{familyId}")
    public ResponseEntity<String> triggerIotEvent(
            @org.springframework.web.bind.annotation.PathVariable Integer familyId,
            @RequestBody IotTestRequestDto requestDto) {
        iotEventService.handleIotEvent(familyId, requestDto.getType());
        return ResponseEntity.ok("IoT 이벤트가 발생했습니다 (" + requestDto.getType() + "). 서버 로그와 알림을 확인하세요.");
    }

    @Operation(summary = "그룹 알림 이력 조회", description = "특정 그룹의 모든 알림을 조회합니다. 읽음/안읽음 상태 포함.")
    @org.springframework.web.bind.annotation.GetMapping("/families/{familyId}/history")
    public ResponseEntity<List<NotificationHistoryResponseDto>> getNotificationHistory(
            @org.springframework.web.bind.annotation.PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Integer userId = userDetails.getId();
        log.info("Fetching notification history for familyId: {}, userId: {}", familyId, userId);
        java.util.List<NotificationHistoryResponseDto> history = notificationService.getNotificationHistory(familyId, userId);
        return ResponseEntity.ok(history);
    }

    // DTO 내부 클래스 (간편함을 위해)
    @lombok.Data
    public static class IotTestRequestDto {
        private String type; // "OUTING" or "RETURN"
    }


}
