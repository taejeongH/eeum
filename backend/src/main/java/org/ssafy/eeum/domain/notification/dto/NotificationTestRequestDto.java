package org.ssafy.eeum.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationTestRequestDto {

    @Schema(description = "가족 ID (그룹 ID)", example = "1")
    private Integer familyId;

    @Schema(description = "타겟 유저 ID (수신자)", example = "1")
    private Integer targetUserId;

    @Schema(description = "알림 제목", example = "테스트 알림")
    private String title;

    @Schema(description = "알림 내용", example = "테스트 메시지입니다.")
    private String message;

    @Schema(description = "알림 타입 (NORMAL, EMERGENCY)", example = "NORMAL")
    private String type;
}
