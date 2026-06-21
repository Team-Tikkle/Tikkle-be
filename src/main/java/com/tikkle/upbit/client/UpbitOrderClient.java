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

import java.util.Map;

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

    public UpbitOrderResponse placeMarketBuyOrder(String market, int krwAmount, String accessKey, String secretKey) {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
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
        } catch (Exception e) {
            log.error("업비트 매수 주문 실패", e);
            throw new UpbitOrderFailedException();
        }
    }

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
            log.error("업비트 주문 내역 조회 실패", e);
            throw new UpbitOrderInquiryFailedException();
        }
    }
}