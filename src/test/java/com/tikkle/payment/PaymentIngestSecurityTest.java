package com.tikkle.payment;

import com.tikkle.support.IntegrationTest;
import com.tikkle.support.PaymentSignatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 수신 API(POST /api/payments)의 서명·타임스탬프 검사를 확인합니다.
 */
class PaymentIngestSecurityTest extends IntegrationTest {

    private static final String PAYMENT_PATH = "/api/payments";
    private static final String SIGNATURE_HEADER = "X-Tikkle-Signature";
    private static final String TIMESTAMP_HEADER = "X-Tikkle-Timestamp";
    private static final String BODY = "{\"userId\":1,\"cardCompany\":\"KBANK\",\"cardNumberLast4\":\"1234\","
            + "\"merchant\":\"스타벅스 강남점\",\"amount\":4500,\"transactionId\":\"tx-security-test\"}";

    @Value("${tikkle.payment.secret-key}")
    private String secretKey;

    @Test
    @DisplayName("서명·타임스탬프 헤더가 없으면 401과 PAYMENT-001을 받는다")
    void rejectsRequestWithoutHeaders() throws Exception {
        mockMvc.perform(paymentRequest(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAYMENT-001"));
    }

    @Test
    @DisplayName("타임스탬프가 숫자가 아니면 401과 PAYMENT-001을 받는다")
    void rejectsNonNumericTimestamp() throws Exception {
        mockMvc.perform(paymentRequest(BODY)
                        .header(TIMESTAMP_HEADER, "not-a-number")
                        .header(SIGNATURE_HEADER, PaymentSignatures.sign(secretKey, BODY, "not-a-number")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAYMENT-001"));
    }

    @ParameterizedTest(name = "현재 시각에서 {0}초 벗어난 타임스탬프")
    @DisplayName("타임스탬프가 5분 넘게 벗어나면 서명이 맞아도 401과 PAYMENT-002를 받는다")
    @ValueSource(longs = {-600, 600})
    void rejectsExpiredTimestamp(long offsetSeconds) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond() + offsetSeconds);

        mockMvc.perform(paymentRequest(BODY)
                        .header(TIMESTAMP_HEADER, timestamp)
                        .header(SIGNATURE_HEADER, PaymentSignatures.sign(secretKey, BODY, timestamp)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAYMENT-002"));
    }

    @Test
    @DisplayName("다른 키로 서명한 요청은 401과 PAYMENT-001을 받는다")
    void rejectsWrongSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(paymentRequest(BODY)
                        .header(TIMESTAMP_HEADER, timestamp)
                        .header(SIGNATURE_HEADER, PaymentSignatures.sign("another-key", BODY, timestamp)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAYMENT-001"));
    }

    @Test
    @DisplayName("서명이 맞으면 서명 검사를 지나 본문 검증 단계까지 간다")
    void passesSignatureCheckWithValidSignature() throws Exception {
        // 필수 값이 빠진 본문을 올바르게 서명해 보낸다. 서명 오류(401)가 아니라 입력 오류(400)가 나오면 서명 검사는 통과한 것이다
        String emptyBody = "{}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(paymentRequest(emptyBody)
                        .header(TIMESTAMP_HEADER, timestamp)
                        .header(SIGNATURE_HEADER, PaymentSignatures.sign(secretKey, emptyBody, timestamp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    private MockHttpServletRequestBuilder paymentRequest(String body) {
        // 본문 캐싱 필터는 서블릿 경로로 결제 수신 요청을 가려낸다. 운영(Tomcat)에서는 서블릿 경로가 요청 경로와 같지만
        // MockMvc 는 비워 두므로 같은 값을 넣어 운영과 같은 필터 경로를 타게 한다
        return post(PAYMENT_PATH)
                .servletPath(PAYMENT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
