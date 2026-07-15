package com.tikkle.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class AppConfig {
    
    @PostConstruct
    public void init() {
        // 애플리케이션 전역 타임존을 한국 시간(KST)으로 강제 고정
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
}