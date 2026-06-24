package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.FearAndGreedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class FearAndGreedClient {
    private final RestClient restClient;

    public FearAndGreedClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.alternative.me")
                .requestFactory(factory)
                .build();
    }

    public String getFearAndGreedIndex() {
        try {
            FearAndGreedResponse response = restClient.get()
                    .uri("/fng/")
                    .retrieve()
                    .body(FearAndGreedResponse.class);

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                return response.getData().get(0).getValue();
            }
            return "50"; // default neutral
        } catch (Exception e) {
            log.warn("Failed to fetch Fear & Greed Index, using default value: {}", e.getMessage());
            return "50"; // default neutral on failure
        }
    }
}