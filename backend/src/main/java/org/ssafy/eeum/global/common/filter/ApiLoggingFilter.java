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

        
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        filterChain.doFilter(requestWrapper, responseWrapper);
        long duration = System.currentTimeMillis() - startTime;

        
        logApiDetails(requestWrapper, responseWrapper, duration);

        
        responseWrapper.copyBodyToResponse();
    }

    private void logApiDetails(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
            long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        int status = response.getStatus();

        
        String requestPayload = new String(request.getContentAsByteArray());
        if (requestPayload.length() > 1000) {
            requestPayload = requestPayload.substring(0, 1000) + "... [TRUNCATED]";
        }

        
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
        
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/favicon.ico");
    }
}
