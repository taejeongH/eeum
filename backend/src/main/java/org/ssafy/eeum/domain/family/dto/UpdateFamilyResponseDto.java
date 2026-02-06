package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(description = "가족 그룹 설정(수정) 응답 DTO")
public class UpdateFamilyResponseDto {
    @Schema(description = "가족 ID")
    private Integer familyId;

    @Schema(description = "가족 이름")
    private String familyName;
}
