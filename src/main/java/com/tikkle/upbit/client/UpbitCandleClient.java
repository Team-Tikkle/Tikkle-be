package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 업비트 차트 캔들 정보를 조회하는 외부 연동 클라이언트입니다.
 * 주로 투자 포트폴리오 차트 등을 그리기 위한 주봉 캔들 조회에 사용됩니다.
 */
@Slf4j
@Component
public class UpbitCandleClient {

    private final RestClient restClient;

    public UpbitCandleClient() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        
        this.restClient = RestClient.builder()
                .baseUrl("https://api.upbit.com/v1")
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    /**
     * 특정 마켓(코인)의 최근 주봉(Weekly Candle) 데이터를 요청한 개수만큼 조회합니다.
     *
     * @param market 조회할 마켓 (예: KRW-BTC)
     * @param count 조회할 캔들 개수
     * @return 주봉 캔들 데이터 리스트 (조회 실패 시 빈 리스트 반환)
     */
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