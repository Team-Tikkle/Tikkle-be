package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitCandleResponse;
import com.tikkle.upbit.exception.UpbitCandleInquiryFailedException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.ParameterizedTypeReference;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * 업비트 차트 캔들 정보를 조회하는 외부 연동 클라이언트입니다.
 * 주로 투자 포트폴리오 차트 등을 그리기 위한 주봉 캔들 조회에 사용됩니다.
 */
@Slf4j
@Component
public class UpbitCandleClient {

    private final RestClient restClient;

    public UpbitCandleClient(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 특정 마켓(코인)의 최근 주봉(Weekly Candle) 데이터를 요청한 개수만큼 조회합니다.
     *
     * @param market 조회할 마켓 (예: KRW-BTC)
     * @param count 조회할 캔들 개수
     * @return 주봉 캔들 데이터 리스트 (응답 본문이 비어 있으면 빈 리스트)
     * @throws UpbitCandleInquiryFailedException 차트 조회 실패 시
     */
    public List<UpbitCandleResponse> getWeeklyCandles(String market, int count) {
        try {
            List<UpbitCandleResponse> candles = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/candles/weeks")
                            .queryParam("market", market)
                            .queryParam("count", count)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitCandleResponse>>() {});

            // 본문이 없는 응답(204 등)에서 null이 반환될 수 있어 호출부의 NPE를 막는다
            return candles != null ? candles : Collections.emptyList();
        } catch (Exception e) {
            log.error("[UpbitCandleClient] 업비트 주봉 캔들 조회 실패 - market: {}", market, e);
            throw new UpbitCandleInquiryFailedException();
        }
    }
}