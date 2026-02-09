package org.ssafy.eeum.domain.family.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.auth.entity.User;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@Schema(description = "가족 멤버 상세 조회 응답 DTO")
public class FamilyMemberDetailResponseDto {
    @Schema(description = "유저 ID")
    private Integer userId;

    @Schema(description = "유저 이름")
    private String name;

    @Schema(description = "유저 전화번호")
    private String phone;

    @Schema(description = "유저 생년월일")
    private LocalDate birthDate;

    @Schema(description = "유저 성별")
    private User.Gender gender;

    @Schema(description = "유저 주소")
    private String address;

    @Schema(description = "유저 프로필 이미지")
    private String profileImage;

    @Schema(description = "유저 혈액형 (피부양자인 경우)")
    private String bloodType;

    @Schema(description = "유저 기저질환 목록 (피부양자인 경우)")
    private List<String> chronicDiseases;

    @Schema(description = "응급 우선순위 (부양자인 경우 1~4)")
    private Integer emergencyPriority;

    @Schema(description = "대표자와의 관계 (부양자인 경우)")
    private String relationship;

    @Schema(description = "피부양자 여부")
    private boolean isDependent;

    @Schema(description = "대표자 여부")
    @JsonProperty("representative")
    private boolean isRepresentative;

    @Schema(description = "현재 사용자가 그룹의 대표자인지 여부")
    private boolean isCurrentUserOwner;

    public static FamilyMemberDetailResponseDto of(User user, Supporter supporter) {
        boolean isPatient = supporter.getRole() == Supporter.Role.PATIENT;
        boolean isOwnerLink = supporter.getFamily().getUser() != null &&
                supporter.getFamily().getUser().getId().equals(supporter.getUser().getId());

        return FamilyMemberDetailResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .address(user.getAddress())
                .profileImage(user.getProfileImage())
                
                .bloodType(isPatient ? user.getBloodType() : null)
                .chronicDiseases(isPatient ? user.getChronicDiseasesList() : null)
                
                .emergencyPriority(isPatient ? null : supporter.getEmergencyPriority())
                .relationship(isPatient ? null : supporter.getRelationship())
                .isDependent(isPatient)
                .isRepresentative(supporter.isRepresentativeFlag() || isOwnerLink)
                .build();
    }
}
