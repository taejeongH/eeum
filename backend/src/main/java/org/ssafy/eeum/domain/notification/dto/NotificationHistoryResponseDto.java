package org.ssafy.eeum.domain.notification.dto;

@lombok.Data
@lombok.Builder
public class NotificationHistoryResponseDto {
    private Long id;
    private String title;
    private String message;
    private String type;
    private java.time.LocalDateTime createdAt;
}