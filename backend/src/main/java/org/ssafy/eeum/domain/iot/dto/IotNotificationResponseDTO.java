package org.ssafy.eeum.domain.iot.dto;

import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.iot.entity.IotNotification;

import java.time.LocalDateTime;

@Getter
@Builder
public class IotNotificationResponseDTO {
    private Long id;
    private String kind;
    private String content;
    private String messageId;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static IotNotificationResponseDTO from(IotNotification notification) {
        return IotNotificationResponseDTO.builder()
                .id(notification.getId())
                .kind(notification.getKind())
                .content(notification.getContent())
                .messageId(notification.getMessageId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
