package com.tikkle.payment;

import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.support.IntegrationTest;
import com.tikkle.support.PaymentIngestRequests;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 수신 API(POST /api/payments)가 본문 크기를 제한하는지 확인합니다.
 */
class PaymentIngestBodySizeTest extends IntegrationTest {

    private static final String TX_KEY_PREFIX = "payment:tx:";
    private static final String TRANSACTION_ID = "tx-body-size-test";
    private static final int MAX_BODY_BYTES = 8 * 1024;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PaymentIngestRequests ingestRequests;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = testData.createUser();
        testData.createLinkedAccount(owner, TwoFactorProvider.KAKAO);
        testData.createSpareChangeRule(owner, PaymentCategory.CAFE, RuleType.ROUND_UP_10000);
        testData.createCategoryMapping("스타벅스", PaymentCategory.CAFE);
        testData.createCoin("KRW-BTC", "비트코인");
    }

    @Test
    @DisplayName("본문이 꼭 8KB인 요청은 그대로 처리한다")
    void acceptsBodyAtLimit() throws Exception {
        String body = paddedBody(MAX_BODY_BYTES);

        mockMvc.perform(ingestRequests.signed(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("PENDING_PURCHASE"));

        assertThat(paymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("본문이 8KB를 넘으면 서명이 맞는 정상 요청이어도 413(COMMON-005)으로 거절한다")
    void rejectsOversizedBodyEvenWithValidSignature() throws Exception {
        String body = paddedBody(MAX_BODY_BYTES + 1);

        mockMvc.perform(ingestRequests.signed(body))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("COMMON-005"));

        assertThat(paymentEventRepository.count()).isZero();
        assertThat(redisTemplate.hasKey(TX_KEY_PREFIX + TRANSACTION_ID)).isFalse();
    }

    @Test
    @DisplayName("본문이 8KB를 넘으면 서명 검사보다 먼저 거절한다")
    void rejectsOversizedBodyBeforeSignatureCheck() throws Exception {
        String body = paddedBody(MAX_BODY_BYTES + 1);

        // 서명·타임스탬프 헤더 없이 보낸다. 서명 검사까지 갔다면 401 이다
        mockMvc.perform(post(PaymentIngestRequests.PATH)
                        .servletPath(PaymentIngestRequests.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("COMMON-005"));
    }

    // 정상 요청 본문 뒤에 공백을 붙여 정확히 주어진 바이트 수로 만든다. JSON 은 끝의 공백을 무시한다
    private String paddedBody(int totalBytes) {
        String json = ingestRequests.body(owner.getId(), "KBANK", "1234", "스타벅스 강남점", 4_500, TRANSACTION_ID);
        int jsonBytes = json.getBytes(StandardCharsets.UTF_8).length;
        return json + " ".repeat(totalBytes - jsonBytes);
    }
}
