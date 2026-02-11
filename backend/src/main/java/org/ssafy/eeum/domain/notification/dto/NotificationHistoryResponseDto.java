package org.ssafy.eeum.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationHistoryResponseDto {
    private Long id;
    private String title;
    private String message;
    private String type;
    @JsonProperty("related_id")
    private Integer relatedId;
    private LocalDateTime createdAt;
    private boolean isRead;

    private String videoUrl;
    private Double confidence;
}