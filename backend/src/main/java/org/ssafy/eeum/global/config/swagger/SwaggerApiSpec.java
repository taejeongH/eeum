package org.ssafy.eeum.global.config.swagger;

import org.ssafy.eeum.global.error.model.ErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SwaggerApiSpec {
    String summary();
    String description() default "";
    int successCode() default 200;
    String successMessage() default "요청 처리에 성공하였습니다.";
    ErrorCode[] errors() default {};
}