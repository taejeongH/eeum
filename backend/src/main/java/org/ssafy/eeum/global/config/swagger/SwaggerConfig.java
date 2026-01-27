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

@Configuration
public class SwaggerConfig {

    @Value("${eeum.swagger-url:http://localhost:8080}")
    private String swaggerUrl;

    @Bean
    public OpenAPI openAPI() {

        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        Server prodServer = new Server();
        prodServer.setUrl("https://i14a105.p.ssafy.io");
        prodServer.setDescription("Production Server (HTTPS)");

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

    private Info apiInfo() {
        return new Info()
                .title("이음(Eeum) API 명세서")
                .description("백엔드 공통 인프라 및 도메인 API 문서입니다.")
                .version("1.0.0");
    }
}