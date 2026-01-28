package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "가족 그룹 설정(수정) 요청 DTO")
public class UpdateFamilyRequestDto {
    @Schema(description = "새로운 가족 그룹 이름 (선택 사항)")
    private String newGroupName;

    @Schema(description = "피부양자로 설정할 유저 ID (선택 사항)")
    private Integer dependentUserId;

    @Schema(description = "피부양자의 혈액형 (선택 사항)")
    private String dependentBloodType;

    @Schema(description = "피부양자의 기저질환 (선택 사항)")
    private List<String> dependentChronicDiseases;

    @Schema(description = "멤버별 응급 우선순위 설정 (선택 사항)")
    private List<FamilyMemberPriorityDto> memberPriorities;
}
