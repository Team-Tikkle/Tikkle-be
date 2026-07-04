package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import com.tikkle.upbit.exception.UpbitTickerInquiryFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class UpbitTickerClient {
    private final RestClient restClient;

    public UpbitTickerClient(@Value("${upbit.api.base-url:https://api.upbit.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public List<UpbitTickerResponse> getTickers(String markets) {
        try {
            List<UpbitTickerResponse> response = restClient.get()
                    .uri("/v1/ticker?markets=" + markets)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitTickerResponse>>() {});
            return response != null ? response : java.util.Collections.emptyList();
        } catch (Exception e) {
            log.error("업비트 시세 조회 실패 (markets: {})", markets, e);
            throw new UpbitTickerInquiryFailedException();
        }
    }
}