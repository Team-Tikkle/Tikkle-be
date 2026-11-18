package com.tikkle.payment;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.support.IntegrationTest;
import com.tikkle.support.PaymentIngestRequests;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import com.tikkle.user.repository.LinkedAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 수신 API(POST /api/payments)가 요청 필드의 길이·형식을 검증하는지 확인합니다.
 */
class PaymentIngestValidationTest extends IntegrationTest {

    private static final String TX_KEY_PREFIX = "payment:tx:";
    private static final String TRANSACTION_ID = "tx-validation-test";
    private static final String CARD_COMPANY = "KBANK";
    private static final String CARD_LAST4 = "1234";
    private static final String MERCHANT = "스타벅스 강남점";

    // 10,000원 단위 올림 규칙에서 4,500원은 잔돈 5,500원(매수 대상)이 된다
    private static final int AMOUNT = 4_500;

    // 원장 컬럼 길이
    private static final int CARD_COMPANY_MAX = 50;
    private static final int MERCHANT_MAX = 100;
    private static final int TRANSACTION_ID_MAX = 255;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private LinkedAccountRepository linkedAccountRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PaymentIngestRequests ingestRequests;

    private User owner;
    private LinkedAccount account;

    @BeforeEach
    void setUp() {
        owner = testData.createUser();
        account = testData.createLinkedAccount(owner, TwoFactorProvider.KAKAO);
        testData.createSpareChangeRule(owner, PaymentCategory.CAFE, RuleType.ROUND_UP_10000);
        testData.createCategoryMapping("스타벅스", PaymentCategory.CAFE);
        testData.createCoin("KRW-BTC", "비트코인");
    }

    @Test
    @DisplayName("각 필드가 컬럼 길이와 꼭 같은 요청은 그대로 처리한다")
    void acceptsFieldsAtColumnLength() throws Exception {
        String cardCompany = "K".repeat(CARD_COMPANY_MAX);
        String merchant = "스타벅스" + "가".repeat(MERCHANT_MAX - 4);
        String transactionId = "t".repeat(TRANSACTION_ID_MAX);
        account.updateKbankInfo(cardCompany, CARD_LAST4);
        linkedAccountRepository.save(account);

        send(cardCompany, CARD_LAST4, merchant, transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("PENDING_PURCHASE"));

        assertThat(savedEvents()).singleElement().satisfies(event -> {
            assertThat(event.getCardCompany()).isEqualTo(cardCompany);
            assertThat(event.getRawMerchant()).isEqualTo(merchant);
            assertThat(event.getTransactionId()).isEqualTo(transactionId);
        });
    }

    @Test
    @DisplayName("가맹점명이 100자를 넘으면 400(COMMON-002)으로 거절하고 분류·추천을 하지 않는다")
    void rejectsMerchantOverLength() throws Exception {
        String merchant = "스타벅스" + "가".repeat(MERCHANT_MAX - 3);

        send(CARD_COMPANY, CARD_LAST4, merchant, TRANSACTION_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        assertNothingProcessed(TRANSACTION_ID);
    }

    @Test
    @DisplayName("거래 ID가 255자를 넘으면 400(COMMON-002)으로 거절하고 분류·추천을 하지 않는다")
    void rejectsTransactionIdOverLength() throws Exception {
        String transactionId = "t".repeat(TRANSACTION_ID_MAX + 1);

        send(CARD_COMPANY, CARD_LAST4, MERCHANT, transactionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        assertNothingProcessed(transactionId);
    }

    @Test
    @DisplayName("카드사명이 50자를 넘으면 카드 불일치가 아니라 400(COMMON-002)으로 거절한다")
    void rejectsCardCompanyOverLength() throws Exception {
        send("K".repeat(CARD_COMPANY_MAX + 1), CARD_LAST4, MERCHANT, TRANSACTION_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        assertNothingProcessed(TRANSACTION_ID);
    }

    @ParameterizedTest(name = "카드 번호 끝자리 \"{0}\"")
    @DisplayName("카드 번호 끝자리가 숫자 4자리가 아니면 카드 불일치가 아니라 400(COMMON-002)으로 거절한다")
    @ValueSource(strings = {"12a4", "123", "12345", "１２３４"})
    void rejectsMalformedCardNumberLast4(String cardNumberLast4) throws Exception {
        send(CARD_COMPANY, cardNumberLast4, MERCHANT, TRANSACTION_ID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        assertNothingProcessed(TRANSACTION_ID);
    }

    private ResultActions send(String cardCompany, String cardNumberLast4, String merchant, String transactionId) throws Exception {
        String body = ingestRequests.body(owner.getId(), cardCompany, cardNumberLast4, merchant, AMOUNT, transactionId);
        return mockMvc.perform(ingestRequests.signed(body));
    }

    private List<PaymentEvent> savedEvents() {
        return paymentEventRepository.findAll();
    }

    // 검증에서 걸린 요청은 원장·멱등 키를 남기지 않고 분류·추천까지 가지 않는다
    private void assertNothingProcessed(String transactionId) {
        assertThat(savedEvents()).isEmpty();
        assertThat(redisTemplate.hasKey(TX_KEY_PREFIX + transactionId)).isFalse();
        verify(aiClassificationService, never()).classify(anyString());
        verify(targetCoinRecommendationService, never()).recommendCoin(any());
    }
}
