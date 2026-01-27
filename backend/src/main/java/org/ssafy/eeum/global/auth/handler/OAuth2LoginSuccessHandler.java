package org.ssafy.eeum.global.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtProvider jwtProvider;
        private final RedisTemplate<String, String> redisTemplate;

        @Value("${spring.profiles.active:local}")
        private String activeProfile;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws IOException {

                CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();
                Integer userId = oAuth2User.getId();

                String accessToken = jwtProvider.createAccessToken(userId, oAuth2User.getEmail(), "ROLE_USER");
                String refreshToken = jwtProvider.createRefreshToken(userId, oAuth2User.getEmail(), "ROLE_USER");

                // Redis에 Refresh Token 저장
                redisTemplate.opsForValue().set("RT:" + userId, refreshToken, 14, TimeUnit.DAYS);

                // [핵심 1] 환경 판정 로직 강화
                // 프로필에 prod가 포함되어 있거나, 요청이 들어온 도메인이 ssafy.io인 경우를 배포 환경으로 판단
                String requestHost = request.getServerName();
                boolean isProdEnv = (activeProfile != null && activeProfile.contains("prod"))
                        || requestHost.contains("i14a105.p.ssafy.io");

                // [핵심 2] 쿠키 생성 (배포 환경에서만 SameSite=None, Secure 적용)
                ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", accessToken)
                        .path("/")
                        .httpOnly(true)
                        .secure(isProdEnv) // HTTPS 환경일 때만 true
                        .sameSite(isProdEnv ? "None" : "Lax")
                        .maxAge(3600)
                        .build();

                ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                        .path("/")
                        .httpOnly(true)
                        .secure(isProdEnv)
                        .sameSite(isProdEnv ? "None" : "Lax")
                        .maxAge(14 * 24 * 3600)
                        .build();

                response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

                // [핵심 3] 리다이렉트 경로 결정
                String targetUrl;
                if (isProdEnv) {
                        // 배포 환경: 메인 도메인으로 리다이렉트
                        targetUrl = "https://i14a105.p.ssafy.io/";
                } else {
                        // 로컬 환경: 뒤로가기 문제 방지를 위해 쿼리 파라미터에 토큰 포함
                        // 프론트에서 이 토큰을 읽어 localStorage에 저장하도록 가이드하세요.
                        targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/")
                                .queryParam("accessToken", accessToken)
                                .build().toUriString();
                }

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
}