package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.family.entity.Family;

@Getter
@Builder
@Schema(description = "가족 생성 응답 DTO")
public class CreateFamilyResponseDto {
    @Schema(description = "가족 ID")
    private Integer id;

    @Schema(description = "가족 이름")
    private String name;

    @Schema(description = "초대 코드")
    private String inviteCode;

    public static CreateFamilyResponseDto of(Family family) {
        return CreateFamilyResponseDto.builder()
                .id(family.getId())
                .name(family.getGroupName())
                .inviteCode(family.getInviteCode())
                .build();
    }
}
