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
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON005", "이미 존재하는 리소스입니다."),

    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH001", "유효하지 않은 토큰입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH002", "이미 가입된 이메일입니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "AUTH003", "인증 코드가 올바르지 않거나 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH004", "이메일 인증이 완료되지 않았습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH005", "이메일 또는 비밀번호가 일치하지 않습니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH006", "이메일 발송에 실패했습니다."),

    // Family 관련 에러 코드
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY001", "유저를 찾을 수 없습니다."),
    FAMILY_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY002", "가족 그룹을 찾을 수 없습니다."),
    SUPPORTER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAMILY003", "가족 구성원 정보를 찾을 수 없습니다."),
    NOT_FAMILY_REPRESENTATIVE(HttpStatus.FORBIDDEN, "FAMILY004", "가족 대표자만 이 작업을 수행할 수 있습니다."),
    INVALID_EMERGENCY_PRIORITY(HttpStatus.BAD_REQUEST, "FAMILY005", "응급 우선순위는 1에서 4 사이여야 합니다."),
    ALREADY_FAMILY_MEMBER(HttpStatus.BAD_REQUEST, "FAMILY006", "이미 해당 가족 그룹의 멤버입니다."),
    ALREADY_FAMILY_REPRESENTATIVE(HttpStatus.BAD_REQUEST, "FAMILY007", "이미 해당 가족 그룹의 대표자입니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "FAMILY008", "유효하지 않은 초대 코드입니다."),
    FORBIDDEN_FAMILY_ACCESS(HttpStatus.FORBIDDEN, "FAMILY009", "해당 가족 그룹에 접근할 권한이 없습니다."),

    // 일정
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE001", "존재하지 않는 일정입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "SCHEDULE002", "종료일은 시작일보다 빠를 수 없습니다."),
    RESERVED_TITLE(HttpStatus.BAD_REQUEST, "SCHEDULE003", "'EXCLUDED'는 사용할 수 없는 일정 제목입니다."),

    // IoT 관련 에러 코드
    IOT_DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "IOT001", "존재하지 않는 IoT 기기입니다."),
    IOT_DEVICE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "IOT002", "기기에 할당된 그룹 정보를 찾을 수 없습니다."),
    IOT_INVALID_PAIRING_CODE(HttpStatus.NOT_FOUND, "IOT003", "유효하지 않거나 만료된 페어링 코드입니다."),
    IOT_UNREGISTERED_SERIAL_NUMBER(HttpStatus.NOT_FOUND, "IOT004", "등록되지 않은 기기 시리얼 번호입니다."),
    IOT_MASTER_DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "IOT005", "마스터 기기 정보가 등록 목록에 없거나 찾을 수 없습니다."),

    // 알림 관련 에러 코드
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI001", "알림 정보를 찾을 수 없습니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI002", "알림 수신 정보를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
