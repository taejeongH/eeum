package org.ssafy.eeum.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

@lombok.Data
@lombok.Builder
public class NotificationHistoryResponseDto {
    private Long id;
    private String title;
    private String message;
    private String type;
    @JsonProperty("related_id")
    private Integer relatedId;
    private java.time.LocalDateTime createdAt;
    private boolean isRead;

    private String videoUrl;
    private Double confidence;
}