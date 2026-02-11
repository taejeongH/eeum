package org.ssafy.eeum.global.config.swagger;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.error.model.ErrorCode;

/**
 * SwaggerApiSpec 어노테이션을 분석하여 Swagger UI에 동적인 응답 예시를 반영하는 클래스입니다.
 * 
 * @summary Swagger 명세 커스텀마이저
 */
@Component
public class SwaggerSpecCustomizer implements OperationCustomizer {

    /**
     * SwaggerApiSpec 어노테이션 정보를 바탕으로 API 오퍼레이션의 명세를 수정합니다.
     * 
     * @summary API 명세 커스터마이징 수행
     * @param operation     Swagger 오퍼레이션 객체
     * @param handlerMethod 대상 핸들러 메서드 정보
     * @return 수정 완료된 Operation 객체
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        SwaggerApiSpec apiSpec = handlerMethod.getMethodAnnotation(SwaggerApiSpec.class);

        if (apiSpec == null) {
            return operation;
        }

        operation.setSummary(apiSpec.summary());
        operation.setDescription(apiSpec.description());

        ApiResponses responses = operation.getResponses();

        String successCode = String.valueOf(apiSpec.successCode());
        String defaultCode = "200";

        if (!successCode.equals(defaultCode) && responses.containsKey(defaultCode)) {
            ApiResponse defaultResponse = responses.get(defaultCode);
            responses.remove(defaultCode);
            defaultResponse.setDescription(apiSpec.successMessage());
            responses.addApiResponse(successCode, defaultResponse);
        } else if (responses.containsKey(successCode)) {
            responses.get(successCode).setDescription(apiSpec.successMessage());
        }

        for (ErrorCode errorCode : apiSpec.errors()) {
            addErrorExample(responses, errorCode);
        }

        return operation;
    }

    /**
     * 에러 응답 예시를 Swagger 명세에 추가합니다.
     * 
     * @summary 에러 예시 추가
     * @param responses Swagger 응답 객체 맵
     * @param errorCode 추가할 에러 코드 정보
     */
    private void addErrorExample(ApiResponses responses, ErrorCode errorCode) {
        String statusCode = String.valueOf(errorCode.getHttpStatus().value());

        if (!responses.containsKey(statusCode)) {
            responses.addApiResponse(statusCode, new ApiResponse());
        }
        ApiResponse response = responses.get(statusCode);

        if (response.getContent() == null) {
            response.setContent(new Content());
        }
        Content content = response.getContent();

        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = new MediaType();
            content.addMediaType("application/json", mediaType);
        }

        Example example = new Example();
        example.setValue(RestApiResponse.fail(errorCode));

        mediaType.addExamples(errorCode.name(), example);
    }
}