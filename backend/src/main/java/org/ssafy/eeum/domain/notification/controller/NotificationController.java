package org.ssafy.eeum.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.health.service.HealthService;
import org.ssafy.eeum.domain.notification.dto.NotificationHistoryResponseDto;
import org.ssafy.eeum.domain.notification.dto.NotificationTestRequestDto;
import org.ssafy.eeum.domain.notification.service.FallDetectionService;
import org.ssafy.eeum.domain.notification.service.IotEventService;
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
    
    private final NotificationService notificationService;
    private final FallDetectionService fallDetectionService;
    private final IotEventService iotEventService;
    private final HealthService healthService;

    @Operation(summary = "알림 생성 및 전송 테스트", description = "알림을 생성하고(DB 저장) 지정된 유저에게 전송합니다.")
    @PostMapping("/test")
    public ResponseEntity<String> createAndSendNotification(@RequestBody NotificationTestRequestDto requestDto) {
        Long notificationId = notificationService.createNotification(
                requestDto.getFamilyId(),
                requestDto.getTitle(),
                requestDto.getMessage(),
                requestDto.getType(),
                null
        );
        notificationService.sendNotification(notificationId, requestDto.getTargetUserId());
        return ResponseEntity.ok("알림이 생성되고 전송되었습니다 (ID: " + notificationId + ")");
    }
    
    @Operation(summary = "알림 읽음 처리", description = "특정 유저가 특정 알림을 읽었음을 표시합니다.")
    @PostMapping("/read")
    public ResponseEntity<String> markAsRead(@RequestBody org.ssafy.eeum.domain.notification.dto.NotificationReadRequestDto requestDto) {
        notificationService.markAsRead(requestDto.getNotificationId(), requestDto.getUserId());
        return ResponseEntity.ok("알림이 읽음 처리되었습니다.");
    }

    @Operation(summary = "낙상 감지 테스트", description = "낙상 이벤트를 발생시켜 우선순위에 따른 순차 발송을 테스트합니다. (심박수 측정 포함)")
    @PostMapping("/fall-test/{familyId}")
    public ResponseEntity<String> triggerFallDetection(@org.springframework.web.bind.annotation.PathVariable Integer familyId) {
        healthService.requestMeasurement(familyId);
        fallDetectionService.handleFallDetection(familyId, "테스트 낙상 감지 발생! (심박수 측정 요청됨)", null);
        
        return ResponseEntity.ok("낙상 감지 및 심박수 측정 요청이 발생했습니다. 워치와 서버 로그를 확인하세요.");
    }

    @Operation(summary = "IoT 이벤트 테스트 (외출/귀가)", description = "IoT 이벤트(OUTING/RETURN)를 발생시켜 보호자 전원에게 알림을 보냅니다.")
    @PostMapping("/iot-test/{familyId}")
    public ResponseEntity<String> triggerIotEvent(
            @PathVariable Integer familyId,
            @RequestBody IotTestRequestDto requestDto) {
        iotEventService.handleIotEvent(familyId, requestDto.getType());
        return ResponseEntity.ok("IoT 이벤트가 발생했습니다 (" + requestDto.getType() + "). 서버 로그와 알림을 확인하세요.");
    }

    @Operation(summary = "그룹 알림 이력 조회", description = "특정 그룹의 모든 알림을 조회합니다. 읽음/안읽음 상태 포함.")
    @GetMapping("/families/{familyId}/history")
    public ResponseEntity<List<NotificationHistoryResponseDto>> getNotificationHistory(
            @PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Integer userId = userDetails.getId();
        List<NotificationHistoryResponseDto> history = notificationService.getNotificationHistory(familyId, userId);
        return ResponseEntity.ok(history);
    }

    @Data
    public static class IotTestRequestDto {
        private String type;
    }
}
