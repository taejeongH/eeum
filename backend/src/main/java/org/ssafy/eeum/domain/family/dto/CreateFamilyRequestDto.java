package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "가족 생성 요청 DTO")
public class CreateFamilyRequestDto {
    @Schema(description = "가족 이름")
    private String name;

    @Schema(description = "대표자의 피부양자와의 관계")
    private String relationship;
}
