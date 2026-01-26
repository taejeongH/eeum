package org.ssafy.eeum.global.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtProvider jwtProvider;
        private final RedisTemplate<String, String> redisTemplate;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException {

                // 1. 로그인 유저 정보 꺼내기
                CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();
                Integer userId = oAuth2User.getId();

                // 2. 토큰 생성
                String accessToken = jwtProvider.createAccessToken(userId, oAuth2User.getEmail(), "ROLE_USER");
                String refreshToken = jwtProvider.createRefreshToken(userId, oAuth2User.getEmail(), "ROLE_USER");

                // 3. Redis에 Refresh Token 저장
                redisTemplate.opsForValue().set(
                        "RT:" + userId,
                        refreshToken,
                        14, TimeUnit.DAYS);

                // 4. 쿠키 설정 (Access Token)
                // HTTPS 배포 환경이므로 secure(true)와 SameSite(None) 설정을 적용합니다.
                ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", accessToken)
                        .path("/")
                        .httpOnly(true)
                        .secure(true)    // HTTPS 환경 필수 설정
                        .sameSite("None") // 프론트와 백엔드 간 쿠키 전달 허용
                        .maxAge(3600)
                        .build();

                // 5. 쿠키 설정 (Refresh Token)
                ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                        .path("/")
                        .httpOnly(true)
                        .secure(true)    // HTTPS 환경 필수 설정
                        .sameSite("None")
                        .maxAge(14 * 24 * 3600)
                        .build();

                // 헤더에 쿠키 추가
                response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

                // 6. 리다이렉트 경로 설정 (가장 중요!)
                // Swagger 주소가 아닌, 실제 사용자가 보게 될 프론트엔드 메인 페이지 주소로 변경합니다.
                String targetUrl = "https://i14a105.p.ssafy.io/";

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}