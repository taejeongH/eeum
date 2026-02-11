package org.ssafy.eeum.global.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * application.yml에 설정된 JWT 관련 속성들을 바인딩하는 설정 클래스입니다.
 * 
 * @summary JWT 환경 설정 프로퍼티
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String privateKeyPath;
    private String publicKeyPath;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
}
