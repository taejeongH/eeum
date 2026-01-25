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

    // Family 관련 에러 코드
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY001", "유저를 찾을 수 없습니다."),
    FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY002", "가족 그룹을 찾을 수 없습니다."),
    SUPPORTER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY003", "가족 구성원 정보를 찾을 수 없습니다."),
    NOT_FAMILY_REPRESENTATIVE(HttpStatus.FORBIDDEN, "FAMILY004", "가족 대표자만 이 작업을 수행할 수 있습니다."),
    INVALID_EMERGENCY_PRIORITY(HttpStatus.BAD_REQUEST, "FAMILY005", "응급 우선순위는 1에서 4 사이여야 합니다."),
    ALREADY_FAMILY_MEMBER(HttpStatus.BAD_REQUEST, "FAMILY006", "이미 해당 가족 그룹의 멤버입니다."),
    ALREADY_FAMILY_REPRESENTATIVE(HttpStatus.BAD_REQUEST, "FAMILY007", "이미 해당 가족 그룹의 대표자입니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "FAMILY008", "유효하지 않은 초대 코드입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
