package org.ssafy.eeum.global.infra.fcm;

import lombok.Getter;

/**
 * FCM 토큰이 더 이상 유효하지 않거나 등록 해제되었을 때 발생하는 예외 클래스입니다.
 * 
 * @summary FCM 등록 해제 토큰 예외
 */
@Getter
public class FcmUnregisteredTokenException extends RuntimeException {
    private final String token;

    public FcmUnregisteredTokenException(String token, String message) {
        super(message);
        this.token = token;
    }

    public FcmUnregisteredTokenException(String token, String message, Throwable cause) {
        super(message, cause);
        this.token = token;
    }
}
