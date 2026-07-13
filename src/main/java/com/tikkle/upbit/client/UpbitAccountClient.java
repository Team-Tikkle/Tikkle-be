package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitAccountResponse;
import com.tikkle.upbit.exception.UpbitAccountInquiryFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 사용자 계좌(보유 자산)를 조회하는 업비트 외부 연동 클라이언트입니다.
 */
@Slf4j
@Component
public class UpbitAccountClient {
    private final RestClient restClient;

    public UpbitAccountClient(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 전체 계좌 자산(포트폴리오)을 조회합니다.
     *
     * @param authorizationToken JWT 토큰 (Bearer 토큰 형식)
     * @return 계좌 내역 리스트
     * @throws UpbitAccountInquiryFailedException 계좌 조회에 실패한 경우
     */
    public List<UpbitAccountResponse> getAccounts(String authorizationToken) {
        try {
            List<UpbitAccountResponse> response = restClient.get()
                    .uri("/v1/accounts")
                    .header("Authorization", authorizationToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpbitAccountResponse>>() {});
            return response != null ? response : java.util.Collections.emptyList();
        } catch (Exception e) {
            log.error("[UpbitAccountClient] 업비트 계좌 조회 실패 - error: {}", e.getMessage(), e);
            throw new UpbitAccountInquiryFailedException();
        }
    }
}