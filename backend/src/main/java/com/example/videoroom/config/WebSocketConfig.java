package com.example.videoroom.config;

import com.example.videoroom.signaling.RoomService;
import com.example.videoroom.signaling.SignalingWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // /ws 경로에 시그널링 핸들러를 등록하고 모든 오리진을 허용한다.
        // 운영 환경에서는 "*" 대신 실제 프론트엔드 호스트로 제한해야 한다.
        registry.addHandler(signalingWebSocketHandler(roomService(), objectMapper()), "/ws")
                .setAllowedOrigins("*");
    }

    @Bean
    public RoomService roomService() {
        // 메모리 기반 룸/세션 관리를 담당하는 서비스.
        return new RoomService();
    }

    @Bean
    public ObjectMapper objectMapper() {
        // WebSocket 핸들러에서 사용하는 JSON 직렬화/역직렬화 도구.
        return new ObjectMapper();
    }

    @Bean
    public SignalingWebSocketHandler signalingWebSocketHandler(RoomService roomService, ObjectMapper objectMapper) {
        // 필요한 의존성을 주입해서 핸들러를 생성한다.
        return new SignalingWebSocketHandler(roomService, objectMapper);
    }
}
