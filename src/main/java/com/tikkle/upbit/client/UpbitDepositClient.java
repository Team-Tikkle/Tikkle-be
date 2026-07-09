package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitDepositResponse;
import com.tikkle.upbit.exception.UpbitDepositFailedException;
import com.tikkle.upbit.exception.UpbitDepositInquiryFailedException;
import com.tikkle.upbit.util.UpbitAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 업비트 원화 입금 관련 API를 호출하는 외부 연동 클라이언트입니다.
 * 원화 입금(2차 인증 발송) 및 입금 내역 조회를 담당합니다.
 */
@Slf4j
@Component
public class UpbitDepositClient {
    private final RestClient restClient;

    public UpbitDepositClient(@Value("${upbit.api.base-url:https://api.upbit.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * 업비트에 지정된 금액만큼 원화 입금(2차 인증 발송)을 요청합니다.
     * 카카오톡 페이 인증 등 2차 인증 알림이 사용자에게 발송됩니다.
     *
     * @param amount 입금 요청 금액 (KRW)
     * @param twoFactorType 2차 인증 방식 (예: "kakao")
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 입금 요청 결과 (UUID 등 포함)
     * @throws UpbitDepositFailedException 입금 요청이 실패한 경우
     */
    public UpbitDepositResponse requestKrwDeposit(int amount, String twoFactorType, String accessKey, String secretKey) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("amount", String.valueOf(amount));
            params.put("two_factor_type", twoFactorType);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            String queryString = sb.toString();
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.post()
                    .uri("/v1/deposits/krw")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(params)
                    .retrieve()
                    .body(UpbitDepositResponse.class);
        } catch (RestClientResponseException e) {
            log.error("[UpbitDepositClient] 업비트 원화 입금 요청 실패 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new UpbitDepositFailedException();
        } catch (Exception e) {
            log.error("[UpbitDepositClient] 업비트 원화 입금 요청 실패", e);
            throw new UpbitDepositFailedException();
        }
    }

    /**
     * 입금 요청 시 발급받은 UUID를 통해 개별 입금 건의 상태를 조회합니다.
     *
     * @param uuid 입금 요청 UUID
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 입금 상태 및 상세 정보
     * @throws UpbitDepositInquiryFailedException 조회에 실패한 경우
     */
    public UpbitDepositResponse getDepositDetails(String uuid, String accessKey, String secretKey) {
        try {
            String queryString = "uuid=" + uuid;
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.get()
                    .uri("/v1/deposit?uuid=" + uuid)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .body(UpbitDepositResponse.class);
        } catch (Exception e) {
            log.error("[UpbitDepositClient] 업비트 개별 입금 내역 조회 실패", e);
            throw new UpbitDepositInquiryFailedException();
        }
    }
}