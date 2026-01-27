package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "가족 멤버 응급 우선순위 DTO")
public class FamilyMemberPriorityDto {
    @Schema(description = "유저 ID")
    private Integer userId;

    @Schema(description = "응급 우선순위 (1~4)")
    private Integer emergencyPriority;
}