package org.ssafy.eeum.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.ssafy.eeum.global.error.model.ErrorCode;

/**
 * API 응답 형식을 통일하기 위한 공통 응답 DTO 클래스입니다.
 * 
 * @summary 공통 API 응답 형식
 * @param <T> 응답 데이터의 타입
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RestApiResponse<T> {
    private final String statusCode;
    private final T data;
    private final String errorCode;
    private final String detail;
    private final String message;

    @Builder(access = AccessLevel.PRIVATE)
    private RestApiResponse(String statusCode, T data, String errorCode,
            String detail, String message) {
        this.statusCode = statusCode;
        this.data = data;
        this.errorCode = errorCode;
        this.detail = detail;
        this.message = message;
    }

    /**
     * 성공 응답을 생성합니다.
     * 
     * @summary 성공 응답 생성 (상세)
     * @param status  HTTP 상태 코드
     * @param message 응답 메시지
     * @param data    응답 데이터
     * @return RestApiResponse 객체
     */
    public static <T> RestApiResponse<T> success(HttpStatus status, String message, T data) {
        return RestApiResponse.<T>builder()
                .statusCode(status.value() + " " + status.name())
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 성공 응답을 생성합니다. (상태 코드 OK)
     * 
     * @summary 성공 응답 생성 (데이터만 포함)
     * @param data 응답 데이터
     * @return RestApiResponse 객체
     */
    public static <T> RestApiResponse<T> success(T data) {
        return RestApiResponse.<T>builder()
                .statusCode(HttpStatus.OK.value() + " " + HttpStatus.OK.name())
                .data(data)
                .build();
    }

    public static <T> RestApiResponse<T> success(String message) {
        return RestApiResponse.<T>builder()
                .statusCode(HttpStatus.OK.value() + " " + HttpStatus.OK.name())
                .message(message)
                .build();
    }

    /**
     * 실패 응답을 생성합니다.
     * 
     * @summary 실패 응답 생성
     * @param errorCode 에러 코드 정보
     * @param detail    상세 에러 내용
     * @return RestApiResponse 객체
     */
    public static <T> RestApiResponse<T> fail(ErrorCode errorCode, String detail) {
        return RestApiResponse.<T>builder()
                .errorCode(errorCode.getCode())
                .message(errorCode.getMessage())
                .detail(detail)
                .build();
    }

    public static <T> RestApiResponse<T> fail(ErrorCode errorCode) {
        return RestApiResponse.<T>builder()
                .errorCode(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }
}
