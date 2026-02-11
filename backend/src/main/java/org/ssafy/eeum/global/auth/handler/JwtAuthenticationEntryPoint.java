package org.ssafy.eeum.global.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.io.IOException;

/**
 * 인증되지 않은 사용자가 보호된 리소스에 접근하려 할 때 401 Unauthorized 에러를 처리하는 클래스입니다.
 * 
 * @summary JWT 인증 실패 진입점
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * 인증 예외 발생 시 JSON 형식의 에러 응답을 클라이언트에게 전달합니다.
     * 
     * @summary 인증 실패 처리 실행
     * @param request       HTTP 요청 객체
     * @param response      HTTP 응답 객체
     * @param authException 인증 예외 객체
     * @throws IOException 입출력 예외
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        ErrorCode errorCode = ErrorCode.INVALID_TOKEN;

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(errorCode.getHttpStatus().value());
        RestApiResponse<Object> errorResponse = RestApiResponse.fail(errorCode);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}