package org.ssafy.eeum.global.error.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON002", "서버 내부 에러가 발생했습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON003", "존재하지 않는 리소스입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON004", "지원하지 않는 HTTP 메서드입니다."),

    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH001", "유효하지 않은 토큰입니다."),

    // 일정
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE001", "존재하지 않는 일정입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "SCHEDULE002", "종료일은 시작일보다 빠를 수 없습니다."),
    RESERVED_TITLE(HttpStatus.BAD_REQUEST, "SCHEDULE003", "'EXCLUDED'는 사용할 수 없는 일정 제목입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
