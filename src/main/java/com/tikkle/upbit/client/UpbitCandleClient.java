package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class UpbitCandleClient {

    private final RestClient restClient;

    public UpbitCandleClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.upbit.com/v1")
                .defaultHeaders(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    public List<UpbitCandleResponse> getWeeklyCandles(String market, int count) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/candles/weeks")
                            .queryParam("market", market)
                            .queryParam("count", count)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitCandleResponse>>() {});
        } catch (Exception e) {
            log.error("Failed to fetch weekly candle for market: {}", market, e);
            return List.of();
        }
    }
}