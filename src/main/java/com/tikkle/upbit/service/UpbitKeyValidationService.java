package com.tikkle.upbit.service;

import com.tikkle.upbit.exception.UpbitApiCallFailedException;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.upbit.util.UpbitAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class UpbitKeyValidationService {
    private final RestClient restClient;

    public UpbitKeyValidationService(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 입력된 업비트 Access Key와 Secret Key를 사용하여 5가지 필수 권한이 모두 존재하는지 검증합니다.
     * (자산조회, 주문조회, 주문하기, 입금조회, 입금하기)
     * 권한이 하나라도 누락되거나 키가 유효하지 않으면 UpbitInvalidKeyException을 발생시킵니다.
     */
    public void validateKeyOrThrow(String accessKey, String secretKey) {
        log.info("[UpbitKeyValidationService] 업비트 API 키 정밀 권한 검증 시작...");

        // 1. 자산 조회 권한 검사 (GET /v1/accounts)
        checkPermission(accessKey, secretKey, "자산 조회", () -> {
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey);
            restClient.get()
                    .uri("/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .toBodilessEntity();
        });

        // 2. 주문 조회 권한 검사 (GET /v1/orders/chance)
        checkPermission(accessKey, secretKey, "주문 조회", () -> {
            String queryString = "market=KRW-BTC";
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);
            restClient.get()
                    .uri("/v1/orders/chance?market=KRW-BTC")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .toBodilessEntity();
        });

        // 3. 주문 하기 권한 검사 (POST /v1/orders - 더미 데이터로 검증)
        checkPermission(accessKey, secretKey, "주문 하기", () -> {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("market", "KRW-INVALID");
            params.put("side", "bid");
            params.put("price", "10");
            params.put("ord_type", "price");

            String queryString = UpbitAuthUtil.buildQueryString(params);
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            restClient.post()
                    .uri("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(params)
                    .retrieve()
                    .toBodilessEntity();
        });

        // 4. 입금 조회 권한 검사 (GET /v1/deposits)
        checkPermission(accessKey, secretKey, "입금 조회", () -> {
            String queryString = "currency=KRW";
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);
            restClient.get()
                    .uri("/v1/deposits?currency=KRW")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .toBodilessEntity();
        });

        // 5. 입금 하기 권한 검사 (POST /v1/deposits/krw - 더미 데이터로 검증)
        checkPermission(accessKey, secretKey, "입금 하기", () -> {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("amount", "10");

            String queryString = UpbitAuthUtil.buildQueryString(params);
            String token = UpbitAuthUtil.generateToken(accessKey, secretKey, queryString);

            restClient.post()
                    .uri("/v1/deposits/krw")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(params)
                    .retrieve()
                    .toBodilessEntity();
        });

        log.info("[UpbitKeyValidationService] 5대 권한 모두 정상 보유 확인 완료");
    }

    private void checkPermission(String accessKey, String secretKey, String permissionName, Runnable apiCaller) {
        try {
            apiCaller.run();
        } catch (UpbitInvalidKeyException e) {
            log.warn("[UpbitKeyValidationService] 권한 부족 감지: {}", permissionName);
            throw e;
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            // 400 등 나머지 에러 코드는 파라미터(더미데이터) 오류이므로 권한은 있다고 간주함
            log.debug("[UpbitKeyValidationService] API 응답 에러 (권한 정상 추정): {} - Status: {}", permissionName, statusCode);
        } catch (Exception e) {
            log.error("[UpbitKeyValidationService] API 호출 중 알 수 없는 에러 발생", e);
            throw new UpbitApiCallFailedException();
        }
    }
}