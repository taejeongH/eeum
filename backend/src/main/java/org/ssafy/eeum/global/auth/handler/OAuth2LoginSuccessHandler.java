package org.ssafy.eeum.global.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();
        Integer userId = oAuth2User.getId();

        String accessToken = jwtProvider.createAccessToken(userId, oAuth2User.getEmail(), "ROLE_USER");
        String refreshToken = jwtProvider.createRefreshToken(userId, oAuth2User.getEmail(), "ROLE_USER");

        // Redis 저장
        redisTemplate.opsForValue().set("RT:" + userId, refreshToken, 14, TimeUnit.DAYS);

        // 로컬 여부 판정
        String referer = request.getHeader("Referer");
        String origin = request.getHeader("Origin");
        boolean isLocalRequest = (referer != null && referer.contains("localhost"))
                || (origin != null && origin.contains("localhost"));

        String targetUrl;
        if (isLocalRequest) {
            // [핵심] Hash 모드(#)를 사용하는 프론트엔드 대응: fragment 사용
            targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/")
                    .fragment("/login?accessToken=" + accessToken) 
                    .build().toUriString();
        } else {
            targetUrl = UriComponentsBuilder.fromUriString("https://i14a105.p.ssafy.io/")
                    .fragment("/login?accessToken=" + accessToken)
                    .build().toUriString();
        }

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}