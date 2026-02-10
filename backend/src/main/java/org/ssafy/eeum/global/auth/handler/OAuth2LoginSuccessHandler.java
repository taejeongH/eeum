package org.ssafy.eeum.global.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 로그인 성공 시 호출되는 핸들러입니다.
 * 인증된 사용자 정보를 기반으로 JWT 토큰(Access/Refresh)을 생성하고 프론트엔드로 리다이렉트합니다.
 * 
 * @summary OAuth2 로그인 성공 핸들러
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.emulator-host:10.0.2.2}")
    private String emulatorHost;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();
        Integer userId = oAuth2User.getId();

        String accessToken = jwtProvider.createAccessToken(userId, oAuth2User.getEmail(), "ROLE_USER");
        String refreshToken = jwtProvider.createRefreshToken(userId, oAuth2User.getEmail(), "ROLE_USER");

        // Redis 저장
        redisTemplate.opsForValue().set("RT:" + oAuth2User.getEmail(), refreshToken, 14, TimeUnit.DAYS);

        // 로컬 여부 판정
        String referer = request.getHeader("Referer");
        String origin = request.getHeader("Origin");
        boolean isEmulator = (referer != null && referer.contains(emulatorHost))
                || (origin != null && origin.contains(emulatorHost));

        String baseRedirectUrl = frontendUrl;
        if (isEmulator && frontendUrl.contains("localhost")) {
            baseRedirectUrl = frontendUrl.replace("localhost", emulatorHost);
        }

        String targetUrl = UriComponentsBuilder.fromUriString(baseRedirectUrl)
                .fragment("/login?accessToken=" + accessToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
