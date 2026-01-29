package org.ssafy.eeum.domain.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FcmMessageRequestDto {
    @Schema(description = "알림 제목", example = "테스트 알림 🔔")
    private String title;

    @Schema(description = "알림 내용", example = "테스트 메시지입니다.")
    private String body;

    @Schema(description = "알림 타입 (NORMAL, EMERGENCY 등)", example = "NORMAL")
    private String type;

    @Schema(description = "수신자 ID (입력 시 해당 유저에게 발송, 미입력 시 본인에게 발송)", example = "1")
    private String targetUserId;
}
