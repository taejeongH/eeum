package org.ssafy.eeum.global.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();
        Integer userId = oAuth2User.getId();
        String userName = oAuth2User.getName();

        log.info("OAuth2 Login Success - UserID: {}, UserName: {}", userId, userName);

        // TODO: JWT 토큰 생성 후 프론트엔드로 리다이렉트
        // 지금은 간단하게 사용자 정보만 응답

        String targetUrl = String.format("http://localhost:3000/oauth/callback?userId=%d&userName=%s",
                userId, userName);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}