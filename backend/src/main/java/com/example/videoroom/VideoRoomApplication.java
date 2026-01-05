package com.example.videoroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot 애플리케이션 진입점.
@SpringBootApplication
public class VideoRoomApplication {
    public static void main(String[] args) {
        // Spring 컨테이너를 부트스트랩하고 내장 웹 서버를 시작한다.
        SpringApplication.run(VideoRoomApplication.class, args);
    }
}
