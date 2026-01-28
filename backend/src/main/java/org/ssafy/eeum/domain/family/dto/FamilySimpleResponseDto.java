package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.family.entity.Family;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Builder
@Schema(description = "가족 간단 조회 응답 DTO")
public class FamilySimpleResponseDto {
    @Schema(description = "가족 ID")
    private Integer id;

    @Schema(description = "가족 이름")
    private String name;

    public static FamilySimpleResponseDto of(Family family) {
        return FamilySimpleResponseDto.builder()
                .id(family.getId())
                .name(family.getGroupName())
                .owner(false) // Default, will be overridden in Service
                .build();
    }

    public static FamilySimpleResponseDto of(Family family, boolean owner) {
        return FamilySimpleResponseDto.builder()
                .id(family.getId())
                .name(family.getGroupName())
                .owner(owner)
                .build();
    }

    @Schema(description = "대표자 여부")
    @JsonProperty("owner")
    private boolean owner;
}
