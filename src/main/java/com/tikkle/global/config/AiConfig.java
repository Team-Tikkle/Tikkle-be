package com.tikkle.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

    /**
     * Spring AI (OpenAI) 등에서 사용하는 RestClient의 기본 타임아웃을 연장합니다.
     * DeepSeek Pro 모델과 같이 추론 시간이 긴(60초 이상) 모델의 타임아웃 에러를 방지합니다.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 30초
        factory.setReadTimeout(180000);   // 180초 (3분)
        return RestClient.builder().requestFactory(factory);
    }
}