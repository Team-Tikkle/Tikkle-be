package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.upbit.exception.UpbitOrderCancelFailedException;
import com.tikkle.upbit.exception.UpbitOrderFailedException;
import com.tikkle.upbit.exception.UpbitOrderInquiryFailedException;
import com.tikkle.upbit.util.UpbitAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    public UpbitOrderClient(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 업비트에 시장가 매수 주문(price 방식)을 요청합니다.
     * 지정한 원화 금액(krwAmount)만큼 전액 매수합니다.
     *
     * <p>identifier는 업비트가 계정 단위로 중복을 거부하는 사용자 지정 주문 식별자입니다.
     * 동일 identifier로 재요청하면 새 주문이 생기지 않으므로 중복 매수를 막을 수 있고,
     * 응답이 유실되어도 {@link #findOrderByIdentifier}로 접수 여부를 확인할 수 있습니다.
     *
     * @param market 매수할 코인 마켓 (예: KRW-BTC)
     * @param krwAmount 매수할 총 금액
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @param identifier 주문 멱등 식별자 (결제 이벤트당 고유)
     * @return 주문 요청 결과
     * @throws UpbitOrderFailedException 매수 주문이 실패한 경우
     */
    public UpbitOrderResponse placeMarketBuyOrder(String market, int krwAmount, String accessKey, String secretKey, String identifier) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("market", market);
            params.put("side", "bid");
            params.put("price", String.valueOf(krwAmount));
            params.put("ord_type", "price");
            params.put("identifier", identifier);

            String queryString = UpbitAuthUtil.buildQueryString(params);
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.post()
                    .uri("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(params)
                    .retrieve()
                    .body(UpbitOrderResponse.class);
        } catch (UpbitInvalidKeyException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("[UpbitOrderClient] 업비트 매수 주문 실패 - identifier: {}, status: {}, body: {}",
                    identifier, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new UpbitOrderFailedException();
        } catch (Exception e) {
            log.error("[UpbitOrderClient] 업비트 매수 주문 실패 - identifier: {}", identifier, e);
            throw new UpbitOrderFailedException();
        }
    }

    /**
     * 주문 요청 시 지정한 identifier로 주문을 조회합니다.
     * 주문 응답이 유실됐거나 중복 요청이 거부된 상황에서 실제 접수 여부를 확인하는 용도이므로,
     * 조회에 실패하거나 주문이 없으면 예외 대신 null을 반환합니다.
     *
     * @param identifier 주문 요청 시 사용한 식별자
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 조회된 주문. 접수된 주문이 없거나 조회에 실패하면 null
     */
    public UpbitOrderResponse findOrderByIdentifier(String identifier, String accessKey, String secretKey) {
        try {
            String queryString = "identifier=" + identifier;
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.get()
                    .uri("/v1/order?identifier=" + identifier)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .body(UpbitOrderResponse.class);
        } catch (UpbitInvalidKeyException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UpbitOrderClient] identifier 주문 조회 실패(미접수로 간주) - identifier: {}", identifier, e);
            return null;
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
        } catch (UpbitInvalidKeyException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("[UpbitOrderClient] 업비트 주문 내역 조회 실패 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new UpbitOrderInquiryFailedException();
        } catch (Exception e) {
            log.error("[UpbitOrderClient] 업비트 주문 내역 조회 실패", e);
            throw new UpbitOrderInquiryFailedException();
        }
    }

    /**
     * 미체결된 주문을 강제 취소합니다.
     *
     * @param uuid 취소할 주문 UUID
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 취소된 주문 내역
     * @throws UpbitOrderCancelFailedException 취소 실패 시
     */
    public UpbitOrderResponse cancelOrder(String uuid, String accessKey, String secretKey) {
        try {
            String queryString = "uuid=" + uuid;
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            return restClient.delete()
                    .uri("/v1/order?uuid=" + uuid)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .body(UpbitOrderResponse.class);
        } catch (UpbitInvalidKeyException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("[UpbitOrderClient] 업비트 주문 취소 실패 - uuid: {}, status: {}", uuid, e.getStatusCode(), e);
            throw new UpbitOrderCancelFailedException();
        } catch (Exception e) {
            log.error("[UpbitOrderClient] 업비트 주문 취소 실패 - uuid: {}", uuid, e);
            throw new UpbitOrderCancelFailedException();
        }
    }
}
