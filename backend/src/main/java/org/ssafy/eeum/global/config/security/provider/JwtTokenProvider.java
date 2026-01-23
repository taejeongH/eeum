package org.ssafy.eeum.global.config.security.provider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.private-key-path}")
    private String privateKeyPath;

    private PrivateKey privateKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenValidTime;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenValidTime;

    @PostConstruct
    protected void init() {
        try {
            // 1. ClassPathResource를 통해 resources 폴더 내부 파일 읽기
            ClassPathResource resource = new ClassPathResource(privateKeyPath);

            String keyContent;
            try (InputStream is = resource.getInputStream()) {
                keyContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // 2. PEM 형식에서 실제 키 데이터만 추출
            String key = keyContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            // 3. PKCS8 스펙으로 PrivateKey 객체 생성
            byte[] decode = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decode);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(keySpec);

            log.info("JWT Private Key 로드 완료: {}", privateKeyPath);
        } catch (Exception e) {
            log.error("JWT 초기화 중 치명적 오류 발생: {}", e.getMessage());
            throw new RuntimeException("JWT 키 파일을 읽을 수 없습니다.", e);
        }
    }

    public String createAccessToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenValidTime))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String createRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + refreshTokenValidTime))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    // 1. 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(privateKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("유효하지 않은 토큰입니다: {}", e.getMessage());
            return false;
        }
    }

    // 2. 토큰에서 유저 ID 추출
    public String getUserId(String token) {
        return Jwts.parser()
                .setSigningKey(privateKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}