package org.ssafy.eeum.global.auth.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT 서명 및 검증에 사용되는 RSA 키 쌍(Public/Private Key)을 파일로부터 로드하는 유틸리티 클래스입니다.
 * 
 * @summary RSA 키 로드 유틸리티
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyUtils {

    private final JwtProperties jwtProperties;

    /**
     * 설정된 경로에서 RSA Private Key를 로드합니다.
     * 
     * @summary RSA Private Key 로드
     * @return 로드된 PrivateKey 객체
     * @throws CustomException 키 로드 실패 시 발생
     */
    public PrivateKey loadPrivateKey() {
        try {
            String content = readPemContent(jwtProperties.getPrivateKeyPath(), "PRIVATE KEY");
            byte[] decoded = Base64.getDecoder().decode(content);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            log.error("다음 경로에서 RSA Private Key를 로드하는 데 실패했습니다: {}", jwtProperties.getPrivateKeyPath(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 설정된 경로에서 RSA Public Key를 로드합니다.
     * 
     * @summary RSA Public Key 로드
     * @return 로드된 PublicKey 객체
     * @throws CustomException 키 로드 실패 시 발생
     */
    public PublicKey loadPublicKey() {
        try {
            String content = readPemContent(jwtProperties.getPublicKeyPath(), "PUBLIC KEY");
            byte[] decoded = Base64.getDecoder().decode(content);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            log.error("다음 경로에서 RSA Public Key를 로드하는 데 실패했습니다: {}", jwtProperties.getPublicKeyPath(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String readPemContent(String path, String keyType) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return content
                    .replace("-----BEGIN " + keyType + "-----", "")
                    .replace("-----END " + keyType + "-----", "")
                    .replaceAll("\\s", "");
        }
    }
}
