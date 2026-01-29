package org.ssafy.eeum.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationReadRequestDto {

    @Schema(description = "알림 ID", example = "1")
    private Long notificationId;

    @Schema(description = "유저 ID (읽은 사람)", example = "1")
    private Integer userId;
}
