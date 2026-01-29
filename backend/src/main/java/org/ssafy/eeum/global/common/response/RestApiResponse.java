package org.ssafy.eeum.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.ssafy.eeum.global.error.model.ErrorCode;

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

    public static <T> RestApiResponse<T> success(HttpStatus status, String message, T data) {
        return RestApiResponse.<T>builder()
                .statusCode(status.value() + " " + status.name())
                .message(message)
                .data(data)
                .build();
    }

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
