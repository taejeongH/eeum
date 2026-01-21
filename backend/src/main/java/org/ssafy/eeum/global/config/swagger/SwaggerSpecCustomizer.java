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

@Component
public class SwaggerSpecCustomizer implements OperationCustomizer {

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