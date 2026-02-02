package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.ssafy.eeum.domain.family.entity.Family;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Builder
@Schema(description = "가족 간단 조회 응답 DTO")
public class FamilySimpleResponseDto {
    @Schema(description = "가족 ID")
    private Integer id;

    @Schema(description = "가족 이름")
    private String name;

    @Schema(description = "관계 (ex: 할머니, 할아버지)")
    @JsonProperty("relationship")
    private String relationship;

    @Schema(description = "피부양자 이름")
    @JsonProperty("dependentName")
    private String dependentName;

    public static FamilySimpleResponseDto of(Family family) {
        return FamilySimpleResponseDto.builder()
                .id(family.getId())
                .name(family.getGroupName())
                .owner(false)
                .build();
    }

    public static FamilySimpleResponseDto of(Family family, boolean owner, String relationship, String dependentName) {
        return FamilySimpleResponseDto.builder()
                .id(family.getId())
                .name(family.getGroupName())
                .owner(owner)
                .relationship(relationship)
                .dependentName(dependentName)
                .build();
    }

    @Schema(description = "대표자 여부")
    @JsonProperty("owner")
    private boolean owner;
}
