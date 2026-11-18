package com.tikkle.payment;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 수신 API(POST /api/payments)가 같은 거래 ID의 요청을 다시 받았을 때의 처리를 확인합니다.
 */
class PaymentIngestIdempotencyTest extends IntegrationTest {

    private static final String TX_KEY_PREFIX = "payment:tx:";
    private static final String TRANSACTION_ID = "tx-idempotency-test";
    private static final String MERCHANT = "스타벅스 강남점";
    private static final String BITCOIN_MARKET = "KRW-BTC";

    // 10,000원 단위 올림 규칙에서 4,500원은 잔돈 5,500원(매수 대상), 9,500원은 잔돈 500원(최소 금액 미달)이 된다
    private static final int AMOUNT_TO_INVEST = 4_500;
    private static final int AMOUNT_BELOW_MINIMUM = 9_500;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private LinkedAccountRepository linkedAccountRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PaymentIngestRequests ingestRequests;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private LinkedAccount account;

    @BeforeEach
    void setUp() {
        owner = testData.createUser();
        account = testData.createLinkedAccount(owner, TwoFactorProvider.KAKAO);
        testData.createSpareChangeRule(owner, PaymentCategory.CAFE, RuleType.ROUND_UP_10000);
        testData.createCategoryMapping("스타벅스", PaymentCategory.CAFE);
    }

    @Test
    @DisplayName("잔돈이 최소 금액 이상인 결제는 승인 대기로 저장되고 처리 표시가 남는다")
    void savesPendingPurchaseAndKeepsKey() throws Exception {
        testData.createCoin(BITCOIN_MARKET, "비트코인");

        send(AMOUNT_TO_INVEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("PENDING_PURCHASE"))
                .andExpect(jsonPath("$.data.spareChange").value(5_500))
                .andExpect(jsonPath("$.data.market").value(BITCOIN_MARKET));

        assertThat(savedEvents()).singleElement()
                .extracting(PaymentEvent::getStatus).isEqualTo(PaymentStatus.PENDING_PURCHASE);
        assertThat(hasTransactionKey()).isTrue();
    }

    @Test
    @DisplayName("잔돈이 최소 금액에 못 미치는 결제는 미투자로 저장되고 처리 표시가 남는다")
    void savesNotInvestedAndKeepsKey() throws Exception {
        send(AMOUNT_BELOW_MINIMUM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("IGNORE_MINIMUM_AMOUNT_UNMET"))
                .andExpect(jsonPath("$.data.spareChange").value(500));

        assertThat(savedEvents()).singleElement()
                .extracting(PaymentEvent::getStatus).isEqualTo(PaymentStatus.NOT_INVESTED);
        assertThat(hasTransactionKey()).isTrue();
    }

    @Test
    @DisplayName("같은 요청이 두 번 오면 두 번째는 중복(PAYMENT-009)으로 거절하고 원장은 한 건이다")
    void rejectsSecondRequestAsDuplicate() throws Exception {
        testData.createCoin(BITCOIN_MARKET, "비트코인");

        send(AMOUNT_TO_INVEST).andExpect(status().isOk());

        send(AMOUNT_TO_INVEST)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT-009"));

        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("중복으로 거절된 요청은 첫 요청이 남긴 처리 표시를 지우지 않는다")
    void keepsKeyAfterDuplicateRejection() throws Exception {
        testData.createCoin(BITCOIN_MARKET, "비트코인");

        send(AMOUNT_TO_INVEST).andExpect(status().isOk());
        send(AMOUNT_TO_INVEST).andExpect(status().isConflict());

        assertThat(hasTransactionKey()).isTrue();
    }

    @Test
    @DisplayName("미투자로 끝난 결제와 같은 요청은 세 번째에도 중복(PAYMENT-009)으로 거절한다")
    void rejectsThirdRequestAfterNotInvested() throws Exception {
        send(AMOUNT_BELOW_MINIMUM).andExpect(status().isOk());
        send(AMOUNT_BELOW_MINIMUM).andExpect(status().isConflict());

        send(AMOUNT_BELOW_MINIMUM)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT-009"));

        assertThat(savedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("승인 대기로 저장된 결제와 같은 요청은 세 번째에도 중복으로 거절하고 코인 추천을 다시 하지 않는다")
    void rejectsThirdRequestWithoutRunningRecommendationAgain() throws Exception {
        testData.createCoin(BITCOIN_MARKET, "비트코인");

        send(AMOUNT_TO_INVEST).andExpect(status().isOk());
        send(AMOUNT_TO_INVEST).andExpect(status().isConflict());

        send(AMOUNT_TO_INVEST)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT-009"));

        assertThat(savedEvents()).hasSize(1);
        verify(targetCoinRecommendationService, times(1)).recommendCoin(owner.getId());
    }

    @Test
    @DisplayName("처리 표시가 사라진 뒤 같은 요청이 와도, 원장에 미투자로 저장된 건이면 중복으로 거절하고 표시를 다시 남긴다")
    void rejectsDuplicateOfNotInvestedEventWhenKeyIsLost() throws Exception {
        send(AMOUNT_BELOW_MINIMUM).andExpect(status().isOk());
        deleteTransactionKey();

        send(AMOUNT_BELOW_MINIMUM)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT-009"));

        assertThat(savedEvents()).hasSize(1);
        assertThat(hasTransactionKey()).isTrue();
    }

    @Test
    @DisplayName("처리 표시가 사라진 뒤 같은 요청이 와도, 원장에 승인 대기로 저장된 건이면 중복으로 거절하고 표시를 다시 남긴다")
    void rejectsDuplicateOfPendingPurchaseEventWhenKeyIsLost() throws Exception {
        testData.createCoin(BITCOIN_MARKET, "비트코인");

        send(AMOUNT_TO_INVEST).andExpect(status().isOk());
        deleteTransactionKey();

        send(AMOUNT_TO_INVEST)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT-009"));

        assertThat(savedEvents()).hasSize(1);
        assertThat(hasTransactionKey()).isTrue();
    }

    @Test
    @DisplayName("원장 저장이 유니크가 아닌 제약 위반으로 실패하면 중복으로 응답하지 않고 처리 표시를 지운다")
    void doesNotTreatOtherConstraintViolationAsDuplicate() throws Exception {
        testData.createCoin(BITCOIN_MARKET, "비트코인");

        // 승인 대기 건은 사유가 비어 있다. 사유 컬럼을 잠시 NOT NULL 로 바꿔 유니크가 아닌 제약 위반을 만든다
        jdbcTemplate.execute("ALTER TABLE payment_events MODIFY reason VARCHAR(255) NOT NULL");
        try {
            send(AMOUNT_TO_INVEST)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("COMMON-001"));
        } finally {
            jdbcTemplate.execute("ALTER TABLE payment_events MODIFY reason VARCHAR(255) NULL");
        }

        assertThat(savedEvents()).isEmpty();
        assertThat(hasTransactionKey()).isFalse();
    }

    @Test
    @DisplayName("등록한 카드와 다른 카드의 결제는 다시 보내도 같은 거절(PAYMENT-008)을 받고 원장과 처리 표시가 남지 않는다")
    void rejectsCardMismatchEveryTime() throws Exception {
        String body = ingestRequests.body(owner.getId(), "KBANK", "9999", MERCHANT, AMOUNT_TO_INVEST, TRANSACTION_ID);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(ingestRequests.signed(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PAYMENT-008"));

            assertThat(hasTransactionKey()).isFalse();
        }

        assertThat(savedEvents()).isEmpty();
    }

    @Test
    @DisplayName("투자를 꺼 둔 사용자의 결제는 다시 보내도 같은 거절(PAYMENT-011)을 받고 원장과 처리 표시가 남지 않는다")
    void rejectsDisabledInvestmentEveryTime() throws Exception {
        account.updateInvestmentStatus(false);
        linkedAccountRepository.save(account);

        for (int attempt = 0; attempt < 2; attempt++) {
            send(AMOUNT_TO_INVEST)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PAYMENT-011"));

            assertThat(hasTransactionKey()).isFalse();
        }

        assertThat(savedEvents()).isEmpty();
    }

    @Test
    @DisplayName("처리 도중 실패한 요청은 처리 표시를 남기지 않아, 원인이 해소된 뒤 다시 보내면 처리된다")
    void processesRetryAfterFailure() throws Exception {
        // 추천할 코인이 없어 처리에 실패한다
        send(AMOUNT_TO_INVEST)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVESTMENT-003"));

        assertThat(savedEvents()).isEmpty();
        assertThat(hasTransactionKey()).isFalse();

        testData.createCoin(BITCOIN_MARKET, "비트코인");

        send(AMOUNT_TO_INVEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("PENDING_PURCHASE"));

        assertThat(savedEvents()).hasSize(1);
        assertThat(hasTransactionKey()).isTrue();
    }

    private ResultActions send(int amount) throws Exception {
        String body = ingestRequests.body(owner.getId(), "KBANK", "1234", MERCHANT, amount, TRANSACTION_ID);
        return mockMvc.perform(ingestRequests.signed(body));
    }

    private List<PaymentEvent> savedEvents() {
        return paymentEventRepository.findAll();
    }

    private boolean hasTransactionKey() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TX_KEY_PREFIX + TRANSACTION_ID));
    }

    private void deleteTransactionKey() {
        redisTemplate.delete(TX_KEY_PREFIX + TRANSACTION_ID);
    }
}
