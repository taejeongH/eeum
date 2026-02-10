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

/**
 * 애플리케이션 전역에서 발생하는 예외를 중앙 집중식으로 처리하는 클래스입니다.
 * 각 예외 타입을 기반으로 적절한 HTTP 상태 코드와 통일된 응답 형식을 반환합니다.
 * 
 * @summary 전역 예외 처리기
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * 비즈니스 로직 중 발생하는 커스텀 예외를 처리합니다.
         * 
         * @summary 커스텀 예외 처리
         * @param e CustomException 객체
         * @return 에러 정보가 담긴 응답 객체
         */
        @ExceptionHandler(CustomException.class)
        protected ResponseEntity<RestApiResponse<?>> handleCustomException(CustomException e) {
                log.warn("커스텀 예외 발생: {} - {}", e.getErrorCode().getCode(), e.getMessage());
                return ResponseEntity
                                .status(e.getErrorCode().getHttpStatus())
                                .body(RestApiResponse.fail(e.getErrorCode()));
        }

        /**
         * @Valid 어노테이션을 통한 객체 검증 실패 시 호출됩니다.
         * @summary DTO 검증 실패 처리
         * @param e MethodArgumentNotValidException 객체
         * @return 입력값 오류 상세 정보가 담긴 응답 객체
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        protected ResponseEntity<RestApiResponse<?>> handleMethodArgumentNotValidException(
                        MethodArgumentNotValidException e) {
                log.warn("입력값 검증 예외 발생: {}", e.getMessage());
                String detail = e.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .collect(Collectors.joining(", "));
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(RestApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE, detail));
        }

        /**
         * 제약 조건 위반(JSR-303/JSR-380) 발생 시 호출됩니다.
         * 
         * @summary 제약 조건 위반 처리
         * @param e ConstraintViolationException 객체
         * @return 제약 조건 위반 상세 정보가 담긴 응답 객체
         */
        @ExceptionHandler(ConstraintViolationException.class)
        protected ResponseEntity<RestApiResponse<?>> handleConstraintViolationException(
                        ConstraintViolationException e) {
                log.warn("제약 조건 위반 발생: {}", e.getMessage());
                String detail = e.getConstraintViolations().stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining(", "));
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(RestApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE, detail));
        }

        /**
         * 지원하지 않는 HTTP 메서드 호출 시 호출됩니다.
         * 
         * @summary HTTP 메서드 미지원 처리
         * @param e HttpRequestMethodNotSupportedException 객체
         * @return 메서드 지원하지 않음 에러 응답 객체
         */
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        protected ResponseEntity<RestApiResponse<?>> handleMethodNotSupported(
                        HttpRequestMethodNotSupportedException e) {
                log.warn("지원하지 않는 HTTP 메서드 호출: {}", e.getMethod());
                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(RestApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage()));
        }

        /**
         * 요청한 리소스를 찾을 수 없을 때(404) 호출됩니다.
         * 
         * @summary 리소스 미지정(404) 처리
         * @param e NoResourceFoundException 객체
         * @return 404 에러 응답 객체
         */
        @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
        protected ResponseEntity<RestApiResponse<?>> handleNoResourceFoundException(
                        org.springframework.web.servlet.resource.NoResourceFoundException e) {
                log.warn("리소스를 찾을 수 없음 (404): {}", e.getResourcePath());
                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(RestApiResponse.fail(ErrorCode.ENTITY_NOT_FOUND,
                                                "Resource not found: " + e.getResourcePath()));
        }

        /**
         * 기타 정의되지 않은 모든 내부 서버 오류를 처리합니다.
         * 
         * @summary 공통 예외(500) 처리
         * @param e 발생한 Exception 객체
         * @return 500 에러 응답 객체
         */
        @ExceptionHandler(Exception.class)
        protected ResponseEntity<RestApiResponse<?>> handleException(Exception e) {
                log.error("내부 서버 오류 발생: ", e);
                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(RestApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage()));
        }
}
