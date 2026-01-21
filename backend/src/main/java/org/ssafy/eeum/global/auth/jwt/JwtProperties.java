package org.ssafy.eeum.global.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
