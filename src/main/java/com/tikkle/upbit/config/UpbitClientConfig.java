package com.tikkle.upbit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 업비트 외부 API 연동을 위한 공통 RestClient 설정 클래스입니다.
 */
@Configuration
public class UpbitClientConfig {

    @Bean
    public RestClient upbitRestClient(@Value("${upbit.api.base-url:https://api.upbit.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }
}