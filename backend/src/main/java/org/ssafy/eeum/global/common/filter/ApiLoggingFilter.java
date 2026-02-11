package org.ssafy.eeum.global.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;

/**
 * API 요청 및 응답 정보를 로깅하기 위한 필터 클래스입니다.
 * 요청 URI, 메소드, 상태 코드, 소요 시간 및 페이로드를 로그로 남깁니다.
 * 
 * @summary API 로깅 필터
 */
@Slf4j
@Component
public class ApiLoggingFilter extends OncePerRequestFilter {
    @Value("${eeum.log.max-payload-length:500}")
    private int maxPayloadLength;

    @Value("${eeum.log.exclude-paths:/swagger-ui,/v3/api-docs,/favicon.ico}")
    private List<String> excludePaths;

    /**
     * 요청을 가로채서 로깅 처리를 수행합니다.
     * 
     * @summary 필터 실행 및 로깅
     * @param request     HttpServletRequest 객체
     * @param response    HttpServletResponse 객체
     * @param filterChain FilterChain 객체
     * @throws ServletException 서블릿 예외 발생 시
     * @throws IOException      입출력 예외 발생 시
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

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

    /**
     * 상세 API 호출 정보를 추출하여 로그로 기록합니다.
     * 
     * @summary API 상세 정보 로깅
     * @param request  캐싱된 요청 객체
     * @param response 캐싱된 응답 객체
     * @param duration 처리 소요 시간 (ms)
     */
    private void logApiDetails(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
            long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        int status = response.getStatus();

        // 요청 페이로드 추출
        String requestPayload = new String(request.getContentAsByteArray());
        if (requestPayload.length() > maxPayloadLength) {
            requestPayload = requestPayload.substring(0, maxPayloadLength) + "... [생략됨]";
        }

        // 응답 페이로드 추출
        String responsePayload = new String(response.getContentAsByteArray());
        if (responsePayload.length() > maxPayloadLength) {
            responsePayload = responsePayload.substring(0, maxPayloadLength) + "... [생략됨]";
        }

        log.info(
                "[API 로그] {} {} | 상태: {} | 클라이언트IP: {} | 소요시간: {}ms | 쿼리스트링: {} | 요청페이로드: {} | 응답페이로드: {}",
                method, uri, status, clientIp, duration,
                queryString != null ? queryString : "없음",
                requestPayload.isEmpty() ? "비어있음" : requestPayload,
                responsePayload.isEmpty() ? "비어있음" : responsePayload);
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
        return excludePaths.stream().anyMatch(path::startsWith);
    }
}
