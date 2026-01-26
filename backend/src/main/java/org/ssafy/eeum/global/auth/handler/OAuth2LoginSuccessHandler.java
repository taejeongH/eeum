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

                // 3. Redis에 Refresh Token 저장 (보안을 위해)
                redisTemplate.opsForValue().set(
                                "RT:" + userId,
                                refreshToken,
                                14, TimeUnit.DAYS);

                // 4. 쿠키 설정 (Access Token)
                ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", accessToken)
                                .path("/")
                                .httpOnly(true) // JS 접근 방지 (보안 핵심)
                                .secure(false) // 로컬(http)은 false, 배포(https)는 true
                                .sameSite("Lax") // CSRF 방지 및 로컬 테스트 호환성
                                .maxAge(3600) // 1시간
                                .build();

                // 5. 쿠키 설정 (Refresh Token - 선택 사항, 쿠키로 관리하면 더 편함)
                ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                                .path("/")
                                .httpOnly(true)
                                .secure(false)
                                .sameSite("Lax")
                                .maxAge(14 * 24 * 3600) // 14일
                                .build();

                // 헤더에 쿠키 추가
                response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

                // 6. 리다이렉트 경로 설정 (핵심 수정 사항)
                // localhost:5173 대신 현재 배포된 서버의 스웨거 주소로 보내서 쿠키가 브라우저에 저장되게 합니다.
                String targetUrl = "http://i14a105.p.ssafy.io:8080/swagger-ui/index.html";
                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}