package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.CoinGeckoGlobalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class CoinGeckoClient {
    private final RestClient restClient;

    public CoinGeckoClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.coingecko.com/api/v3")
                .requestFactory(factory)
                .build();
    }

    public String getBtcDominance() {
        try {
            CoinGeckoGlobalResponse response = restClient.get()
                    .uri("/global")
                    .retrieve()
                    .body(CoinGeckoGlobalResponse.class);

            if (response != null && response.getData() != null 
                    && response.getData().getMarket_cap_percentage() != null) {
                Double btcDom = response.getData().getMarket_cap_percentage().get("btc");
                if (btcDom != null) {
                    return String.format("%.1f", btcDom);
                }
            }
            return "50.0"; // default
        } catch (Exception e) {
            log.warn("Failed to fetch BTC Dominance from CoinGecko, using default value: {}", e.getMessage());
            return "50.0"; // default on failure
        }
    }
}