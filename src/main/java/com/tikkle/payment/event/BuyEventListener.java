package com.tikkle.payment.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BuyEventListener {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBuyEvent(BuyEvent event) {
        log.info("[Buy Event Triggered] PaymentEvent ID: {}, Ticker: {}, Stock: {}, Amount: {}",
                event.paymentEventId(), event.ticker(), event.stockName(), event.amount());
        // 실제 매수 API 호출 로직이 이곳에 작성됩니다.
    }
}