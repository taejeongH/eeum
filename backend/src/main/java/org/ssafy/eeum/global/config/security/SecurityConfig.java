package org.ssafy.eeum.global.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.ssafy.eeum.global.auth.filter.JwtAuthenticationFilter;
import org.ssafy.eeum.global.auth.handler.OAuth2LoginSuccessHandler;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.oauth2.CustomOAuth2UserService;

import java.util.List;

/**
 * 애플리케이션의 전반적인 보안 정책(인증, 인가, CORS 등)을 설정하는 핵심 구성 클래스입니다.
 * 
 * @summary Spring Security 설정 클래스
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
        private final JwtProvider jwtProvider;

        @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
        private List<String> allowedOrigins;

        /**
         * 비밀번호 암호화를 위한 BCryptPasswordEncoder를 빈으로 등록합니다.
         * 
         * @summary PasswordEncoder Bean 생성
         * @return BCryptPasswordEncoder 객체
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /**
         * 정적 리소스(Swagger UI, Favicon 등)에 대해 보안 필터링을 제외하도록 설정합니다.
         * 
         * @summary 웹 보안 무시 경로 설정
         * @return WebSecurityCustomizer 객체
         */
        @Bean
        public WebSecurityCustomizer webSecurityCustomizer() {
                return (web) -> web.ignoring().requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/favicon.ico");
        }

        /**
         * HTTP 보안 정책(인증 경로, 필터 순서, 예외 처리 등)을 정의합니다.
         * 
         * @summary SecurityFilterChain 구성
         * @param http HttpSecurity 객체
         * @return 구성된 SecurityFilterChain 객체
         * @throws Exception 보안 설정 중 오류 발생 시
         */
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/", "/api/auth/**", "/login/**", "/oauth2/**",
                                                                "/api/ws/**")
                                                .permitAll()
                                                // IoT 인증 API - 토큰 불필요
                                                .requestMatchers("/api/iot/auth/**")
                                                .permitAll()
                                                // 보이스 웹후크 - 토큰 불필요
                                                .requestMatchers("/api/voice/webhook/**")
                                                .permitAll()
                                                // IoT 기기 전용 API - ROLE_DEVICE 필수
                                                .requestMatchers("/api/iot/device/**")
                                                .hasRole("DEVICE")
                                                .anyRequest().authenticated())
                                .exceptionHandling(exception -> exception
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                request -> request.getRequestURI().startsWith("/api/")))
                                .oauth2Login(oauth2 -> oauth2
                                                .authorizationEndpoint(authorization -> authorization
                                                                .baseUri("/api/auth/login/social"))
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2LoginSuccessHandler))
                                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * CORS(Cross-Origin Resource Sharing) 관련 정책을 설정합니다.
         * 
         * @summary CORS 설정 정보 생성
         * @return CorsConfigurationSource 객체
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOriginPatterns(allowedOrigins);
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                config.setExposedHeaders(List.of("Authorization", "Set-Cookie"));

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }
}