package org.ssafy.eeum.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.ssafy.eeum.domain.iot.handler.StreamingHandler;

/**
 * WebSocket 통신(STOMP 또는 Raw WebSocket)을 정의하고 핸들러를 관리하는 설정 클래스입니다.
 * 
 * @summary WebSocket 서비스 설정 클래스
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final StreamingHandler streamingHandler;

    /**
     * WebSocket 핸들러를 특정 엔드포인트에 등록하고 허용 도메인을 설정합니다.
     * 
     * @summary WebSocket 핸들러 등록
     * @param registry WebSocket 핸들러 레지스트리
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamingHandler, "/api/ws/stream")
                .setAllowedOriginPatterns("*"); // Allow all origins for dev/testing
    }

    /**
     * WebSocket 서버 컨테이너의 제한 정책(버퍼 사이즈, 세션 타임아웃 등)을 구성합니다.
     * 
     * @summary WebSocket 컨테이너 설정
     * @return ServletServerContainerFactoryBean 객체
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024); // 2MB
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L); // 30 Minutes
        return container;
    }
}
