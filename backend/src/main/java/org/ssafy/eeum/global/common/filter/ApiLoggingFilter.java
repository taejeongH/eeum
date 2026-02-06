package org.ssafy.eeum.global.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Slf4j
@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // ContentCachingWrapper를 사용하여 HTTP body를 캐싱 (한 번 읽어도 다시 읽을 수 있게 함)
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        filterChain.doFilter(requestWrapper, responseWrapper);
        long duration = System.currentTimeMillis() - startTime;

        // API 호출 정보를 로그로 기록
        logApiDetails(requestWrapper, responseWrapper, duration);

        // 캐시된 응답 내용을 실제 응답에 복사 (필수)
        responseWrapper.copyBodyToResponse();
    }

    private void logApiDetails(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
            long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        int status = response.getStatus();

        // 요청 페이로드 추출 (최대 1000자 제한)
        String requestPayload = new String(request.getContentAsByteArray());
        if (requestPayload.length() > 1000) {
            requestPayload = requestPayload.substring(0, 1000) + "... [TRUNCATED]";
        }

        // 응답 페이로드 추출 (최대 1000자 제한)
        String responsePayload = new String(response.getContentAsByteArray());
        if (responsePayload.length() > 1000) {
            responsePayload = responsePayload.substring(0, 1000) + "... [TRUNCATED]";
        }

        log.info(
                "[API LOG] {} {} | Status: {} | ClientIP: {} | Time: {}ms | Query: {} | ReqPayload: {} | ResPayload: {}",
                method, uri, status, clientIp, duration,
                queryString != null ? queryString : "N/A",
                requestPayload.isEmpty() ? "EMPTY" : requestPayload,
                responsePayload.isEmpty() ? "EMPTY" : responsePayload);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 정적 리소스나 스웨거 문서는 로깅에서 제외
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/favicon.ico");
    }
}
