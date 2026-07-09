package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.CoinGeckoGlobalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * CoinGecko Open API를 호출하여 암호화폐 시장의 글로벌 매크로 지표를 수집하는 클라이언트입니다.
 */
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

    /**
     * CoinGecko 글로벌 엔드포인트를 호출하여 현재 비트코인 도미넌스(BTC 시가총액 비중)를 가져옵니다.
     * 외부 통신 실패 시에는 기본값(50.0)을 반환하여 시스템 장애를 방지합니다.
     *
     * @return 비트코인 도미넌스 퍼센트 (ex: "52.4")
     */
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
            log.warn("[CoinGeckoClient] 코인게코 비트코인 도미넌스 조회 실패. 기본값 사용 - errorMessage: {}", e.getMessage());
            return "50.0"; // default on failure
        }
    }
}