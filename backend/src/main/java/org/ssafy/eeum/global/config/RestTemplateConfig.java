package org.ssafy.eeum.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 외부 API와의 동기 통신을 지원하는 RestTemplate의 설정을 담당하는 클래스입니다.
 * 
 * @summary RestTemplate 설정 클래스
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 공통으로 사용할 RestTemplate Bean을 생성합니다.
     * 
     * @summary RestTemplate Bean 생성
     * @return 생성된 RestTemplate 객체
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
