package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import com.tikkle.upbit.exception.UpbitTickerInquiryFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * 특정 마켓(코인)의 현재가(Ticker) 정보를 조회하는 외부 연동 클라이언트입니다.
 * 포트폴리오 수익률 계산, 코인 추천 등 실시간 시세가 필요한 곳에서 호출됩니다.
 */
@Slf4j
@Component
public class UpbitTickerClient {
    private final RestClient restClient;

    public UpbitTickerClient(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 쉼표(,)로 구분된 마켓 코드 목록을 받아 해당 코인들의 현재 시세(Ticker)를 조회합니다.
     *
     * @param markets 조회할 마켓 코드 목록 (예: "KRW-BTC,KRW-ETH")
     * @return 시세 정보 리스트
     * @throws UpbitTickerInquiryFailedException 시세 조회에 실패한 경우
     */
    public List<UpbitTickerResponse> getTickers(String markets) {
        try {
            List<UpbitTickerResponse> response = restClient.get()
                    .uri("/v1/ticker?markets=" + markets)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitTickerResponse>>() {});
            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            log.error("[UpbitTickerClient] 업비트 시세 조회 실패 - markets: {}", markets, e);
            throw new UpbitTickerInquiryFailedException();
        }
    }
}