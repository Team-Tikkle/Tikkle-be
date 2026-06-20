package com.tikkle.payment.event;

import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.kis.client.KisAuthClient;
import com.tikkle.kis.client.KisOrderClient;
import com.tikkle.kis.dto.KisOrderRequest;
import com.tikkle.kis.dto.KisOrderResponse;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * 장중 자동 매수(ORDERING) 이벤트를 비동기로 처리합니다.
 * KIS 증권사 API를 호출하여 실제 매수를 실행하고, 결과에 따라 원장과 포트폴리오를 갱신합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuyEventListener {
    private final KisAuthClient kisAuthClient;
    private final KisOrderClient kisOrderClient;
    private final PaymentEventRepository paymentEventRepository;
    private final PortfolioRepository portfolioRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleBuyEvent(BuyEvent event) {
        log.info("[BuyEvent] Processing - PaymentEventId={}, userId={}, ticker={}, amount={}",
                event.paymentEventId(), event.userId(), event.ticker(), event.amount());

        PaymentEvent paymentEvent = paymentEventRepository.findById(event.paymentEventId())
                .orElse(null);
        if (paymentEvent == null) {
            log.error("[BuyEvent] PaymentEvent not found. id={}", event.paymentEventId());
            return;
        }

        LinkedAccount account = linkedAccountRepository.findByUserId(event.userId())
                .orElse(null);
        if (account == null) {
            log.error("[BuyEvent] LinkedAccount not found. userId={}", event.userId());
            paymentEvent.failInvestment("연동된 증권 계좌를 찾을 수 없습니다.");
            return;
        }

        try {
            // 1. KIS 토큰 발급 (Cache-Aside)
            String accessToken = kisAuthClient.getAccessToken(
                    event.userId(), account.getKisAppKey(), account.getKisAppSecret());

            // 2. KIS 매수 주문 실행
            KisOrderRequest orderRequest = new KisOrderRequest(
                    account.getKisAccountNum(), event.ticker(), event.amount(),
                    accessToken, account.getKisAppKey(), account.getKisAppSecret());

            KisOrderResponse response = kisOrderClient.buyByAmount(orderRequest);

            // 3. 매수 성공 → PaymentEvent 상태 변경 + Portfolio Upsert
            paymentEvent.completeInvestment();

            User user = userRepository.findById(event.userId()).orElseThrow();
            upsertPortfolio(user, event.ticker(), event.amount());

            log.info("[BuyEvent] Buy completed. userId={}, ticker={}, amount={}",
                    event.userId(), event.ticker(), event.amount());

        } catch (Exception e) {
            log.error("[BuyEvent] Buy failed. userId={}, ticker={}, error={}",
                    event.userId(), event.ticker(), e.getMessage());
            paymentEvent.failInvestment("KIS 매수 실패: " + e.getMessage());
        }
    }

    /**
     * Portfolio Upsert: 기존 보유 종목이면 수량/평단가 갱신, 없으면 신규 생성.
     * MVP 단계에서는 체결 단가를 매수 금액으로 간주합니다.
     */
    private void upsertPortfolio(User user, String ticker, int amount) {
        // MVP: 소수점 매수에서는 실제 체결 단가/수량을 KIS 체결 내역 조회 API로 가져와야 하지만,
        // 현재는 매수 금액을 그대로 사용합니다. (추후 체결 내역 조회 API 연동 시 교체)
        BigDecimal executedPrice = BigDecimal.valueOf(amount);
        BigDecimal executedQuantity = BigDecimal.ONE;

        portfolioRepository.findByUserAndTicker(user, ticker)
                .ifPresentOrElse(
                        existing -> existing.updateHolding(executedPrice, executedQuantity),
                        () -> portfolioRepository.save(Portfolio.builder()
                                .user(user)
                                .ticker(ticker)
                                .quantity(executedQuantity)
                                .averagePrice(executedPrice)
                                .build())
                );
    }
}