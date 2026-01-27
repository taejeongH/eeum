package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "초대 정보 응답 DTO")
public class InviteInfoResponseDto {
    @Schema(description = "그룹 이름")
    private String groupName;

    @Schema(description = "초대자 (대표자) 이름")
    private String inviterName;
}
