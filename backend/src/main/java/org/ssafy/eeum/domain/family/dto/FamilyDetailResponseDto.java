package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "가족 상세 조회 응답 DTO")
public class FamilyDetailResponseDto {
    @Schema(description = "가족 ID")
    private Integer familyId;

    @Schema(description = "가족 이름")
    private String groupName;

    private Integer dependentUserId;

    @Schema(description = "멤버별 우선순위 목록")
    private List<FamilyMemberPriorityDto> memberPriorities;

    @Schema(description = "전체 멤버 목록")
    private List<FamilyMemberDto> members;
}
