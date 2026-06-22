package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitMarketResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class UpbitMarketClient {
    private final RestClient restClient;

    public UpbitMarketClient(@Value("${upbit.api.base-url:https://api.upbit.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public List<UpbitMarketResponse> getAllMarkets() {
        try {
            return restClient.get()
                    .uri("/v1/market/all?isDetails=false")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitMarketResponse>>() {});
        } catch (Exception e) {
            log.error("업비트 마켓 리스트 조회 실패", e);
            throw new RuntimeException("업비트 마켓 리스트 조회에 실패했습니다.", e);
        }
    }
}