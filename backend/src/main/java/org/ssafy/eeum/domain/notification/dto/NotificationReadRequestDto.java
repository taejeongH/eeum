package org.ssafy.eeum.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationReadRequestDto {

    @JsonProperty("notification_id")
    @Schema(description = "알림 ID", example = "1")
    private Long notificationId;

    @JsonProperty("user_id")
    @Schema(description = "유저 ID (읽은 사람)", example = "1")
    private Integer userId;
}
