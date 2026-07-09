package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.FearAndGreedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Alternative.me API를 호출하여 현재 암호화폐 시장의 공포 탐욕 지수(Fear & Greed Index)를 수집하는 클라이언트입니다.
 */
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

    /**
     * 공포 탐욕 지수 엔드포인트를 호출하여 현재 시장의 투심 지표(0~100)를 가져옵니다.
     * 외부 통신 실패 시에는 중립값(50)을 반환하여 시스템 장애를 방지합니다.
     *
     * @return 공포 탐욕 지수
     */
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
            log.warn("[FearAndGreedClient] 공포 탐욕 지수 조회 실패. 기본값 사용 - errorMessage: {}", e.getMessage());
            return "50"; // default neutral on failure
        }
    }
}