package org.ssafy.eeum.global.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JWT 액세스 토큰 및 리프레시 토큰 정보를 전달하기 위한 데이터 전송 객체(DTO)입니다.
 * 
 * @summary JWT 토큰 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenDto {
    private String grantType;
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
}
