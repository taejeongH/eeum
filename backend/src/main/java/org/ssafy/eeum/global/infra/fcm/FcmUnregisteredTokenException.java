package org.ssafy.eeum.global.infra.fcm;

import lombok.Getter;

/**
 * FCM 토큰이 유효하지 않음(UNREGISTERED)을 알리는 예외입니다.
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
