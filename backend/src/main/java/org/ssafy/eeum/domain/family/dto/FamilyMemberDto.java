package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.ssafy.eeum.domain.family.entity.Supporter;

@Getter
@Setter
@Builder
@Schema(description = "가족 멤버 정보 DTO")
public class FamilyMemberDto {
    @Schema(description = "유저 ID")
    private Long userId;

    @Schema(description = "유저 이름")
    private String name;

    @Schema(description = "프로필 이미지")
    private String profileImage;

    @Schema(description = "피부양자 여부")
    private boolean isDependent;

    @Schema(description = "응급 우선순위")
    private Integer emergencyPriority;

    public static FamilyMemberDto of(Supporter supporter) {
        return FamilyMemberDto.builder()
                .userId(supporter.getUser().getId().longValue())
                .name(supporter.getUser().getName())
                .profileImage(supporter.getUser().getProfileImage())
                .isDependent(supporter.getRole() == Supporter.Role.PATIENT)
                .emergencyPriority(supporter.getEmergencyPriority())
                .build();
    }
}