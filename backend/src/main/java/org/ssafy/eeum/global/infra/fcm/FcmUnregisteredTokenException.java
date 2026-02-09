package org.ssafy.eeum.global.infra.fcm;

import lombok.Getter;


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
