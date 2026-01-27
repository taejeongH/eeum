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

        // 현재 실행 중인 프로필이 무엇인지 가져옵니다.
        @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:local}")
        private String activeProfile;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException {

                CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();
                Integer userId = oAuth2User.getId();

                String accessToken = jwtProvider.createAccessToken(userId, oAuth2User.getEmail(), "ROLE_USER");
                String refreshToken = jwtProvider.createRefreshToken(userId, oAuth2User.getEmail(), "ROLE_USER");

                redisTemplate.opsForValue().set("RT:" + userId, refreshToken, 14, TimeUnit.DAYS);

                // 로컬 환경인지 확인 (프로필명이 'prod'가 아니면 로컬로 간주)
                boolean isProd = "prod".equals(activeProfile);

                // 쿠키 설정 분기 처리
                ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", accessToken)
                        .path("/")
                        .httpOnly(true)
                        .secure(isProd)    // 배포 서버(HTTPS)일 때만 true, 로컬(HTTP)은 false
                        .sameSite(isProd ? "None" : "Lax") // 로컬에서는 Lax 권장
                        .maxAge(3600)
                        .build();

                ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                        .path("/")
                        .httpOnly(true)
                        .secure(isProd)    // 배포 서버(HTTPS)일 때만 true, 로컬(HTTP)은 false
                        .sameSite(isProd ? "None" : "Lax")
                        .maxAge(14 * 24 * 3600)
                        .build();

                response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

                // 리다이렉트 경로 분기 처리
                String targetUrl = isProd ? "https://i14a105.p.ssafy.io/" : "http://localhost:5173/";
                // ※ 5173은 Vite 기본 포트입니다. 본인의 프론트엔드 포트에 맞춰 수정하세요!

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}