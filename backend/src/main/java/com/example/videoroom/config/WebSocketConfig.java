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
        registry.addHandler(signalingWebSocketHandler(roomService(), objectMapper()), "/ws")
                .setAllowedOrigins("*");
    }

    @Bean
    public RoomService roomService() {
        return new RoomService();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public SignalingWebSocketHandler signalingWebSocketHandler(RoomService roomService, ObjectMapper objectMapper) {
        return new SignalingWebSocketHandler(roomService, objectMapper);
    }
}
