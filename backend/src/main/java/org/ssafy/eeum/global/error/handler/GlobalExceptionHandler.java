package org.ssafy.eeum.global.error.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<RestApiResponse<?>> handleCustomException(CustomException e) {
        log.warn("Custom Exception: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(RestApiResponse.fail(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<RestApiResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Validation/Bind Exception: {}", e.getMessage());
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(RestApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE, detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<RestApiResponse<?>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("Constraint Violation: {}", e.getMessage());
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(RestApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE, detail));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<RestApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method Not Supported: {}", e.getMethod());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(RestApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<RestApiResponse<?>> handleException(Exception e) {
        log.error("Internal Server Error: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage()));
    }
}
