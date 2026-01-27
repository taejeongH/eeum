package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(description = "가족 탈퇴/삭제 응답 DTO")
public class LeaveFamilyResponseDto {
    @Schema(description = "유저가 속한 다음 가족 그룹 ID (선택 사항)")
    private Long nextFamilyId;

    @Schema(description = "유저가 속한 다음 가족 그룹 이름 (선택 사항)")
    private String nextFamilyName;

    @Schema(description = "탈퇴/삭제 후 반환될 메시지 (선택 사항)")
    private String message;
}
