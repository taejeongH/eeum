package org.ssafy.eeum.global.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ssafy.eeum.global.common.filter.ApiLoggingFilter;

/**
 * API 요청 및 응답 로깅을 위한 필터를 설정하는 클래스입니다.
 * 
 * @summary API 로깅 설정 클래스
 */
@Configuration
public class LogConfig {

    /**
     * API 로깅 필터를 FilterRegistrationBean으로 등록합니다.
     * 
     * @summary API 로깅 필터 등록
     * @param apiLoggingFilter 등록할 로깅 필터 Bean
     * @return 필터 등록 정보를 담은 FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> apiLoggingFilterRegistration(ApiLoggingFilter apiLoggingFilter) {
        FilterRegistrationBean<ApiLoggingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(apiLoggingFilter);
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
