package org.ssafy.eeum.global.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;

import java.io.IOException;

/**
 * 모든 HTTP 요청에 대해 JWT 토큰의 유효성을 검사하는 보안 필터입니다.
 * 유효한 토큰이 존재할 경우 해당 사용자의 인증 정보를 SecurityContext에 저장합니다.
 * 
 * @summary JWT 인증 필터
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    /**
     * HTTP 요청 헤더에서 JWT 토큰을 추출하고 유효성을 검증한 후 인증 정보를 설정합니다.
     * 
     * @summary 인증 필터 내부 로직 수행
     * @param request     HTTP 요청 객체
     * @param response    HTTP 응답 객체
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException      입출력 예외
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = resolveToken(request);

        if (StringUtils.hasText(jwt) && jwtProvider.validateToken(jwt)) {
            Authentication authentication = jwtProvider.getAuthentication(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Security Context에 '{}' 인증 정보를 저장했습니다, uri: {}", authentication.getName(),
                    request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // /api/auth/test는 JWT 필터를 거쳐야 하므로 제외
        if (path.equals("/api/auth/test")) {
            return false;
        }
        return path.startsWith("/api/auth/") || path.startsWith("/login/") || path.startsWith("/oauth2/")
                || path.startsWith("/api/voice/webhook/") || path.startsWith("/api/iot/auth/");
    }
}