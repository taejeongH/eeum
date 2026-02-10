package org.ssafy.eeum.global.error.exception;

import lombok.Getter;
import org.ssafy.eeum.global.error.model.ErrorCode;

/**
 * 비즈니스 로직 처리 중 발생하는 예외를 정의하기 위한 공통 커스텀 예외 클래스입니다.
 * 
 * @summary 공통 커스텀 예외 클래스
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
