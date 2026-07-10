package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitMarketResponse;
import com.tikkle.upbit.exception.UpbitMarketInquiryFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 업비트에서 거래 가능한 마켓(코인) 목록을 조회하는 외부 연동 클라이언트입니다.
 * 코인 메타데이터 동기화 스케줄러 등에서 호출됩니다.
 */
@Slf4j
@Component
public class UpbitMarketClient {
    private final RestClient restClient;

    public UpbitMarketClient(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 업비트에서 거래 가능한 전체 마켓(코인) 목록을 조회합니다.
     * 유의종목 등의 디테일한 정보는 제외(isDetails=false)하고 기본 메타데이터만 가져옵니다.
     *
     * @return 마켓 메타데이터 리스트
     * @throws UpbitMarketInquiryFailedException 마켓 목록 조회에 실패한 경우
     */
    public List<UpbitMarketResponse> getAllMarkets() {
        try {
            List<UpbitMarketResponse> response = restClient.get()
                    .uri("/v1/market/all?isDetails=false")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitMarketResponse>>() {});
            return response != null ? response : java.util.Collections.emptyList();
        } catch (Exception e) {
            log.error("[UpbitMarketClient] 업비트 마켓 리스트 조회 실패", e);
            throw new UpbitMarketInquiryFailedException();
        }
    }
}