package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.CoinGeckoCategoryDto;
import com.tikkle.investment.dto.response.CoinGeckoGlobalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.stream.Collectors;

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
     * 외부 통신 실패 시에는 "Unknown (Data fetch failed)"을 반환합니다.
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
            return "Unknown (Data fetch failed)";
        } catch (Exception e) {
            log.warn("[CoinGeckoClient] 코인게코 비트코인 도미넌스 조회 실패. 기본값 사용 - errorMessage: {}", e.getMessage());
            return "Unknown (Data fetch failed)";
        }
    }

    /**
     * CoinGecko 카테고리 엔드포인트를 호출하여 24시간 변동률 기준 상위 테마를 가져옵니다.
     * 외부 통신 실패 시에는 "Unknown (Data fetch failed)"을 반환합니다.
     *
     * @return 주도 테마 문자열 (ex: "Artificial Intelligence, Meme")
     */
    public String getTopHotNarratives() {
        try {
            CoinGeckoCategoryDto[] categories = restClient.get()
                    .uri("/coins/categories")
                    .retrieve()
                    .body(CoinGeckoCategoryDto[].class);

            if (categories != null && categories.length > 0) {
                return Arrays.stream(categories)
                        .filter(c -> c.getMarketCapChange24h() != null)
                        .sorted((c1, c2) -> Double.compare(c2.getMarketCapChange24h(), c1.getMarketCapChange24h()))
                        .limit(3)
                        .map(CoinGeckoCategoryDto::getName)
                        .collect(Collectors.joining(", "));
            }
            return "Unknown (Data fetch failed)";
        } catch (Exception e) {
            log.warn("[CoinGeckoClient] 코인게코 테마 카테고리 조회 실패 - errorMessage: {}", e.getMessage());
            return "Unknown (Data fetch failed)";
        }
    }
}