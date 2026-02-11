package org.ssafy.eeum.global.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI(Swagger)를 활용하여 REST API 문서 자동화를 지원하는 설정 클래스입니다.
 * 
 * @summary Swagger API 문서 구성 클래스
 */
@Configuration
public class SwaggerConfig {

        @Value("${eeum.swagger-url:http://localhost:8080}")
        private String swaggerUrl;

        /**
         * OpenAPI 설정을 정의합니다. 서버 정보, 보안 요구사항, 컴포넌트 스캔 등을 구성합니다.
         * 
         * @summary OpenAPIBean 생성
         * @return 구성된 OpenAPI 객체
         */
        @Bean
        public OpenAPI openAPI() {

                Server localServer = new Server();
                localServer.setUrl("http://localhost:8080");
                localServer.setDescription("Local Development Server");

                Server prodServer = new Server();
                prodServer.setUrl(swaggerUrl);
                prodServer.setDescription("Environment Server");

                String jwtSchemeName = "jwtAuth";
                SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
                Components components = new Components()
                                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                                                .name(jwtSchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT"));

                return new OpenAPI()
                                .info(new Info()
                                                .title("Eeum API Specification")
                                                .description("이음 프로젝트 API 명세서입니다.")
                                                .version("v1.0.0"))
                                .servers(List.of(prodServer, localServer))
                                .addSecurityItem(securityRequirement)
                                .components(components);
        }

        /**
         * API 문서의 기본 정보를 설정하는 헬퍼 메서드입니다.
         * 
         * @summary API 기본 정보 설정
         * @return Info 객체
         */
        private Info apiInfo() {
                return new Info()
                                .title("이음(Eeum) API 명세서")
                                .description("백엔드 공통 인프라 및 도메인 API 문서입니다.")
                                .version("1.0.0");
        }
}