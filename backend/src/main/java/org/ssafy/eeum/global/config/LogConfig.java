package org.ssafy.eeum.global.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ssafy.eeum.global.common.filter.ApiLoggingFilter;

@Configuration
public class LogConfig {

    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> apiLoggingFilterRegistration(ApiLoggingFilter apiLoggingFilter) {
        FilterRegistrationBean<ApiLoggingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(apiLoggingFilter);
        registrationBean.addUrlPatterns("/api/*"); // /api/로 시작하는 모든 요청에 적용
        registrationBean.setOrder(1); // 우선순위 설정
        return registrationBean;
    }
}
