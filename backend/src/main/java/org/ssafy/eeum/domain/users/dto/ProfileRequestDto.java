package org.ssafy.eeum.domain.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.ssafy.eeum.domain.auth.entity.User.Gender;

import java.time.LocalDate;

@Getter
@Schema(description = "프로필 수정 요청 DTO")
public class ProfileRequestDto {
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
}