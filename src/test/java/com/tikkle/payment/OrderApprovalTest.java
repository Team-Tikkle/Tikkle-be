package com.tikkle.payment;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.support.IntegrationTest;
import com.tikkle.support.UpbitMockServer;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매수 승인·거절 API가 지키는 조건을 확인합니다.
 */
class OrderApprovalTest extends IntegrationTest {

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = testData.createUser();
    }

    @Test
    @DisplayName("승인 대기 건을 승인하면 업비트에 입금을 요청하고 입금 대기가 된다")
    void approvesPendingPurchase() throws Exception {
        testData.createLinkedAccount(owner, TwoFactorProvider.KAKAO);
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), PaymentStatus.PENDING_PURCHASE);
        UpbitMockServer.enqueueJson(201, """
                {"type": "deposit", "uuid": "deposit-uuid-1", "currency": "KRW", "state": "PROCESSING", "amount": "5500"}
                """);

        mockMvc.perform(post("/api/payments/{eventId}/approve", event.getId())
                        .header("Authorization", testData.bearerToken(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        PaymentEvent saved = paymentEventRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING_DEPOSIT);
        assertThat(saved.getDepositUuid()).isEqualTo("deposit-uuid-1");

        List<RecordedRequest> requests = UpbitMockServer.receivedRequests();
        assertThat(requests).extracting(RecordedRequest::getPath).contains("/v1/deposits/krw");
    }

    @ParameterizedTest(name = "{0} 상태")
    @DisplayName("승인 대기가 아닌 건은 승인할 수 없고(PAYMENT-006) 업비트를 호출하지 않는다")
    @EnumSource(value = PaymentStatus.class, names = "PENDING_PURCHASE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsApprovalWhenNotPendingPurchase(PaymentStatus status) throws Exception {
        testData.createLinkedAccount(owner, TwoFactorProvider.KAKAO);
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), status);

        mockMvc.perform(post("/api/payments/{eventId}/approve", event.getId())
                        .header("Authorization", testData.bearerToken(owner.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT-006"));

        assertThat(paymentEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(UpbitMockServer.receivedRequests()).isEmpty();
    }

    @Test
    @DisplayName("남의 결제는 없는 결제와 똑같이 승인할 수 없다(PAYMENT-004)")
    void rejectsApprovalOfAnotherUsersEvent() throws Exception {
        User other = testData.createUser();
        testData.createLinkedAccount(other, TwoFactorProvider.KAKAO);
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), PaymentStatus.PENDING_PURCHASE);

        mockMvc.perform(post("/api/payments/{eventId}/approve", event.getId())
                        .header("Authorization", testData.bearerToken(other.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT-004"));

        assertThat(paymentEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING_PURCHASE);
        assertThat(UpbitMockServer.receivedRequests()).isEmpty();
    }

    @Test
    @DisplayName("2차 인증 수단이 없으면 승인할 수 없고(USER-004) 업비트를 호출하지 않는다")
    void rejectsApprovalWithoutTwoFactorProvider() throws Exception {
        testData.createLinkedAccount(owner, null);
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), PaymentStatus.PENDING_PURCHASE);

        mockMvc.perform(post("/api/payments/{eventId}/approve", event.getId())
                        .header("Authorization", testData.bearerToken(owner.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER-004"));

        assertThat(paymentEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING_PURCHASE);
        assertThat(UpbitMockServer.receivedRequests()).isEmpty();
    }

    @Test
    @DisplayName("승인 대기 건을 거절하면 미투자가 된다")
    void rejectsPendingPurchase() throws Exception {
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), PaymentStatus.PENDING_PURCHASE);

        mockMvc.perform(post("/api/payments/{eventId}/reject", event.getId())
                        .header("Authorization", testData.bearerToken(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        assertThat(paymentEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.NOT_INVESTED);
        assertThat(UpbitMockServer.receivedRequests()).isEmpty();
    }

    @ParameterizedTest(name = "{0} 상태")
    @DisplayName("승인 대기가 아닌 건은 거절할 수 없다(PAYMENT-006)")
    @EnumSource(value = PaymentStatus.class, names = "PENDING_PURCHASE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsRejectionWhenNotPendingPurchase(PaymentStatus status) throws Exception {
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), status);

        mockMvc.perform(post("/api/payments/{eventId}/reject", event.getId())
                        .header("Authorization", testData.bearerToken(owner.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT-006"));

        assertThat(paymentEventRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("남의 결제는 거절할 수 없다(PAYMENT-004)")
    void rejectsRejectionOfAnotherUsersEvent() throws Exception {
        User other = testData.createUser();
        PaymentEvent event = testData.createPaymentEvent(owner.getId(), PaymentStatus.PENDING_PURCHASE);

        mockMvc.perform(post("/api/payments/{eventId}/reject", event.getId())
                        .header("Authorization", testData.bearerToken(other.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT-004"));

        assertThat(paymentEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING_PURCHASE);
    }
}
