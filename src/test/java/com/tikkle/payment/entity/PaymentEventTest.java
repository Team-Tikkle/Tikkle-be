package com.tikkle.payment.entity;

import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 이벤트가 매수 흐름을 따라 상태를 바꿀 때 함께 채워지는 값을 확인합니다.
 * 실제 흐름과 같은 순서(승인 대기 → 입금 대기 → 체결 대기 → 투자 완료)로 상태를 옮기며 검증합니다.
 */
class PaymentEventTest {

    private static final String DEPOSIT_UUID = "deposit-uuid-1";
    private static final String TRADE_UUID = "trade-uuid-1";

    @Test
    @DisplayName("승인하면 입금 대기가 되고 입금 uuid와 요청 시각이 남는다")
    void movesToPendingDeposit() {
        PaymentEvent event = pendingPurchaseEvent();

        event.updateToPendingDeposit(DEPOSIT_UUID);

        assertThat(event.getStatus()).isEqualTo(PaymentStatus.PENDING_DEPOSIT);
        assertThat(event.getDepositUuid()).isEqualTo(DEPOSIT_UUID);
        assertThat(event.getDepositRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문이 접수되면 체결 대기가 되고 주문 uuid와 요청 시각이 남는다")
    void movesToPendingTrade() {
        PaymentEvent event = pendingDepositEvent();

        event.updateToPendingTrade(TRADE_UUID);

        assertThat(event.getStatus()).isEqualTo(PaymentStatus.PENDING_TRADE);
        assertThat(event.getTradeUuid()).isEqualTo(TRADE_UUID);
        assertThat(event.getTradeRequestedAt()).isNotNull();
        assertThat(event.getDepositUuid()).isEqualTo(DEPOSIT_UUID);
    }

    @Test
    @DisplayName("체결되면 투자 완료가 되고 체결 수량과 평단가가 남는다")
    void completesInvestment() {
        PaymentEvent event = pendingDepositEvent();
        event.updateToPendingTrade(TRADE_UUID);

        event.completeInvestment(new BigDecimal("0.00007312"), new BigDecimal("75150000.0000"));

        assertThat(event.getStatus()).isEqualTo(PaymentStatus.INVESTED);
        assertThat(event.getInvestedVolume()).isEqualByComparingTo("0.00007312");
        assertThat(event.getInvestedPrice()).isEqualByComparingTo("75150000");
        assertThat(event.getReason()).isNull();
        assertThat(event.getTradeUuid()).isEqualTo(TRADE_UUID);
    }

    @Test
    @DisplayName("승인 대기 건을 건너뛰면 미투자가 되고 사유가 남는다")
    void skipsInvestment() {
        PaymentEvent event = pendingPurchaseEvent();

        event.skipInvestment("사용자에 의한 매수 거절");

        assertThat(event.getStatus()).isEqualTo(PaymentStatus.NOT_INVESTED);
        assertThat(event.getReason()).isEqualTo("사용자에 의한 매수 거절");
    }

    @Test
    @DisplayName("매수에 실패하면 실패가 되고 사유가 남는다")
    void failsInvestment() {
        PaymentEvent event = pendingDepositEvent();

        event.failInvestment("업비트 입금 거절");

        assertThat(event.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(event.getReason()).isEqualTo("업비트 입금 거절");
    }

    @Test
    @DisplayName("입금 대기에서 되돌리면 다시 승인 대기가 되고 사유가 남는다")
    void revertsToPendingPurchase() {
        PaymentEvent event = pendingDepositEvent();

        event.revertToPendingPurchase("2차 인증 시간 초과");

        assertThat(event.getStatus()).isEqualTo(PaymentStatus.PENDING_PURCHASE);
        assertThat(event.getReason()).isEqualTo("2차 인증 시간 초과");
    }

    @Test
    @DisplayName("카테고리를 바꿔도 상태와 잔돈은 그대로다")
    void updatesCategoryOnly() {
        PaymentEvent event = pendingPurchaseEvent();

        event.updateCategory(PaymentCategory.FOOD);

        assertThat(event.getCategory()).isEqualTo(PaymentCategory.FOOD);
        assertThat(event.getStatus()).isEqualTo(PaymentStatus.PENDING_PURCHASE);
        assertThat(event.getSpareChange()).isEqualTo(5500);
    }

    private PaymentEvent pendingPurchaseEvent() {
        return PaymentEvent.builder()
                .userId(1L)
                .cardCompany("KBANK")
                .cardNumberLast4("1234")
                .merchant("스타벅스")
                .rawMerchant("스타벅스 강남점")
                .amount(4500)
                .spareChange(5500)
                .category(PaymentCategory.CAFE)
                .transactionId("tx-entity-test")
                .status(PaymentStatus.PENDING_PURCHASE)
                .build();
    }

    private PaymentEvent pendingDepositEvent() {
        PaymentEvent event = pendingPurchaseEvent();
        event.updateToPendingDeposit(DEPOSIT_UUID);
        return event;
    }
}
