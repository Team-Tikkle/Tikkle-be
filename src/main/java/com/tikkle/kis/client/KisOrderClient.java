package com.tikkle.kis.client;

import com.tikkle.kis.dto.request.KisOrderRequest;
import com.tikkle.kis.dto.response.KisOrderResponse;
import com.tikkle.kis.exception.KisOrderFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * KIS 국내 주식 소수점 매수 주문 클라이언트.
 * 금액(KRW) 기반으로 소수점 매수를 실행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisOrderClient {
    private final RestClient kisRestClient;

    // KIS 모의투자 국내 주식 주문 Transaction ID
    private static final String TR_ID_BUY = "VTTC0802U";

    /**
     * 금액 기반 국내 주식 소수점 매수 주문을 실행합니다.
     * 최대 3회 재시도 + 지수 백오프(Exponential Backoff) 적용.
     */
    public KisOrderResponse buyByAmount(KisOrderRequest request) {
        int maxRetries = 3;
        long baseDelayMs = 200;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 계좌번호에서 앞 8자리(종합계좌번호)와 뒤 2자리(상품코드) 분리
                String acctPrefix = request.accountNumber().substring(0, 8);
                String acctSuffix = request.accountNumber().substring(8, 10);

                Map<String, String> body = Map.of(
                        "CANO", acctPrefix,
                        "ACNT_PRDT_CD", acctSuffix,
                        "PDNO", request.ticker(),
                        "ORD_DVSN", "01",           // 시장가 주문
                        "ORD_QTY", "0",              // 금액 기반 매수 시 수량은 0
                        "ORD_UNPR", "0",             // 시장가이므로 단가 0
                        "CTAC_TLNO", "",
                        "COST_ICLD_YN", "Y",         // 비용 포함 여부
                        "AMT_ORD_YN", "Y",           // 금액 기반 주문 여부
                        "ORD_AMT", String.valueOf(request.amount())
                );

                KisOrderResponse response = kisRestClient.post()
                        .uri("/uapi/domestic-stock/v1/trading/order-cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("authorization", "Bearer " + request.accessToken())
                        .header("appkey", request.appKey())
                        .header("appsecret", request.appSecret())
                        .header("tr_id", TR_ID_BUY)
                        .body(body)
                        .retrieve()
                        .body(KisOrderResponse.class);

                if (response != null && response.isSuccess()) {
                    log.info("[KIS Order] Buy order success. ticker={}, amount={}, orderNo={}",
                            request.ticker(), request.amount(),
                            response.output() != null ? response.output().orderNo() : "N/A");
                    return response;
                }

                String errorMsg = response != null ? response.message() : "Empty response";
                log.warn("[KIS Order] Buy order rejected. attempt={}/{}, msg={}",
                        attempt, maxRetries, errorMsg);

                if (attempt == maxRetries) {
                    throw new KisOrderFailedException();
                }
            } catch (KisOrderFailedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[KIS Order] Buy order attempt {}/{} failed: {}",
                        attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw new KisOrderFailedException();
                }
            }
            // 지수 백오프: 200ms, 400ms, 800ms
            sleep(baseDelayMs * (1L << (attempt - 1)));
        }
        throw new KisOrderFailedException();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}