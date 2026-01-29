package org.ssafy.eeum.domain.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.entity.User.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Schema(description = "프로필 응답 DTO")
public class ProfileResponseDto {
    @Schema(description = "유저 ID")
    private Integer id;
    @Schema(description = "이름")
    private String name;
    @Schema(description = "전화번호")
    private String phone;
    @Schema(description = "생년월일")
    private LocalDate birthDate;
    @Schema(description = "성별")
    private Gender gender;
    @Schema(description = "주소")
    private String address;
    @Schema(description = "프로필 이미지")
    private String profileImage;
    @Schema(description = "수정일")
    private LocalDateTime updatedAt;
    @Schema(description = "가족 ID")
    private Integer familyId;

    @Builder
    public ProfileResponseDto(Integer id, String name, String phone, LocalDate birthDate, Gender gender, String address,
            String profileImage, LocalDateTime updatedAt, Integer familyId) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.gender = gender;
        this.address = address;
        this.profileImage = profileImage;
        this.updatedAt = updatedAt;
        this.familyId = familyId;
    }

    public static ProfileResponseDto of(User user) {
        Integer familyId = null;
        if (user.getSupporters() != null && !user.getSupporters().isEmpty()) {
            familyId = user.getSupporters().get(0).getFamily().getId();
        }

        return ProfileResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .address(user.getAddress())
                .updatedAt(user.getUpdatedAt())
                .familyId(familyId)
                .build();
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }
}
