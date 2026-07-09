package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.upbit.exception.UpbitOrderFailedException;
import com.tikkle.upbit.exception.UpbitOrderInquiryFailedException;
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
 * 업비트 주문 관련 API를 호출하는 외부 연동 클라이언트입니다.
 * 시장가 매수 주문 요청과 개별 주문 상세 내역 조회를 수행합니다.
 */
@Slf4j
@Component
public class UpbitOrderClient {
    private final RestClient restClient;

    public UpbitOrderClient(@Value("${upbit.api.base-url:https://api.upbit.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5초
        factory.setReadTimeout(5000);    // 5초
        
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * 업비트에 시장가 매수 주문(price 방식)을 요청합니다.
     * 지정한 원화 금액(krwAmount)만큼 전액 매수합니다.
     *
     * @param market 매수할 코인 마켓 (예: KRW-BTC)
     * @param krwAmount 매수할 총 금액
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 주문 요청 결과
     * @throws UpbitOrderFailedException 매수 주문이 실패한 경우
     */
    public UpbitOrderResponse placeMarketBuyOrder(String market, int krwAmount, String accessKey, String secretKey) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("market", market);
            params.put("side", "bid");
            params.put("price", String.valueOf(krwAmount));
            params.put("ord_type", "price");

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            String queryString = sb.toString();
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.post()
                    .uri("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(params)
                    .retrieve()
                    .body(UpbitOrderResponse.class);
        } catch (RestClientResponseException e) {
            log.error("[UpbitOrderClient] 업비트 매수 주문 실패 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new UpbitOrderFailedException();
        } catch (Exception e) {
            log.error("[UpbitOrderClient] 업비트 매수 주문 실패", e);
            throw new UpbitOrderFailedException();
        }
    }

    /**
     * 생성된 주문의 상세 내역(체결 상태, 거래 내역 등)을 UUID로 조회합니다.
     *
     * @param uuid 조회할 주문 UUID
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 주문 상세 내역 및 체결 정보
     * @throws UpbitOrderInquiryFailedException 조회 실패 시
     */
    public UpbitOrderResponse getOrderDetails(String uuid, String accessKey, String secretKey) {
        try {
            String queryString = "uuid=" + uuid;
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.get()
                    .uri("/v1/order?uuid=" + uuid)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .body(UpbitOrderResponse.class);
        } catch (Exception e) {
            log.error("[UpbitOrderClient] 업비트 주문 내역 조회 실패", e);
            throw new UpbitOrderInquiryFailedException();
        }
    }
}