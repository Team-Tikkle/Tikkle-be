package com.tikkle.investment.service;

import com.tikkle.investment.entity.InvestmentOrder;
import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.entity.enums.OrderStatus;
import com.tikkle.investment.repository.InvestmentOrderRepository;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.kis.client.KisAuthClient;
import com.tikkle.kis.client.KisOrderClient;
import com.tikkle.kis.dto.request.KisOrderRequest;
import com.tikkle.kis.dto.response.KisOrderResponse;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 장외 대기 건(PENDING)을 userId+ticker 기준으로 합산하여 일괄 매수합니다.
 * 유저 단위로 트랜잭션을 격리하여 한 유저의 실패가 다른 유저에게 전파되지 않도록 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOrderAggregator {
    private final PaymentEventRepository paymentEventRepository;
    private final InvestmentOrderRepository investmentOrderRepository;
    private final PortfolioRepository portfolioRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;
    private final KisAuthClient kisAuthClient;
    private final KisOrderClient kisOrderClient;

    /**
     * PENDING 상태의 모든 PaymentEvent를 조회하여 유저별로 일괄 매수를 실행합니다.
     */
    public void processAllPendingOrders() {
        List<PaymentEvent> pendingEvents = paymentEventRepository.findByStatus(PaymentStatus.PENDING);
        if (pendingEvents.isEmpty()) {
            log.info("[Aggregator] No pending orders to process.");
            return;
        }

        log.info("[Aggregator] Found {} pending payment events.", pendingEvents.size());

        // userId 기준으로 1차 그룹핑
        Map<Long, List<PaymentEvent>> eventsByUser = pendingEvents.stream()
                .collect(Collectors.groupingBy(PaymentEvent::getUserId));

        for (Map.Entry<Long, List<PaymentEvent>> userEntry : eventsByUser.entrySet()) {
            Long userId = userEntry.getKey();
            List<PaymentEvent> userEvents = userEntry.getValue();

            try {
                processUserPendingOrders(userId, userEvents);
            } catch (Exception e) {
                log.error("[Aggregator] Failed to process pending orders for userId={}. error={}",
                        userId, e.getMessage());
            }

            // [TODO: Rate Limit 방어] MVP에서는 200ms 쓰로틀링.
            // 실 서비스 시 Resilience4j RateLimiter로 교체 필요.
            sleep(200);
        }
    }

    /**
     * 특정 유저의 PENDING 이벤트를 ticker 기준으로 합산하여 일괄 매수합니다.
     * 유저 단위 트랜잭션 격리를 위해 별도 메서드로 분리합니다.
     */
    @Transactional
    public void processUserPendingOrders(Long userId, List<PaymentEvent> userEvents) {
        LinkedAccount account = linkedAccountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            log.warn("[Aggregator] LinkedAccount not found for userId={}. Skipping.", userId);
            userEvents.forEach(e -> e.failInvestment("연동된 증권 계좌를 찾을 수 없습니다."));
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[Aggregator] User not found for userId={}. Skipping.", userId);
            return;
        }

        // ticker 기준으로 2차 그룹핑 → 금액 합산
        Map<String, List<PaymentEvent>> eventsByTicker = userEvents.stream()
                .collect(Collectors.groupingBy(e -> {
                    // PaymentEvent에는 ticker가 없으므로, 현재 더미 종목을 사용
                    // TODO: PaymentEvent에 ticker 필드 추가 또는 InvestmentTarget에서 조회
                    return "005930";
                }));

        String accessToken = kisAuthClient.getAccessToken(
                userId, account.getKisAppKey(), account.getKisAppSecret());

        for (Map.Entry<String, List<PaymentEvent>> tickerEntry : eventsByTicker.entrySet()) {
            String ticker = tickerEntry.getKey();
            List<PaymentEvent> tickerEvents = tickerEntry.getValue();

            int totalAmount = tickerEvents.stream()
                    .mapToInt(PaymentEvent::getSpareChange)
                    .sum();

            // 주문 원장 생성
            InvestmentOrder order = InvestmentOrder.builder()
                    .user(user)
                    .ticker(ticker)
                    .totalAmount(totalAmount)
                    .status(OrderStatus.PENDING)
                    .build();
            investmentOrderRepository.save(order);

            try {
                // KIS 매수 주문
                KisOrderRequest orderRequest = new KisOrderRequest(
                        account.getKisAccountNum(), ticker, totalAmount,
                        accessToken, account.getKisAppKey(), account.getKisAppSecret());

                KisOrderResponse response = kisOrderClient.buyByAmount(orderRequest);

                // 성공: 주문 원장 EXECUTED, PaymentEvent들 INVESTED, Portfolio Upsert
                order.markExecuted();
                tickerEvents.forEach(PaymentEvent::completeInvestment);
                upsertPortfolio(user, ticker, totalAmount);

                log.info("[Aggregator] Batch buy success. userId={}, ticker={}, totalAmount={}, eventCount={}",
                        userId, ticker, totalAmount, tickerEvents.size());

            } catch (Exception e) {
                // 실패: 주문 원장 FAILED, PaymentEvent들 FAILED
                order.markFailed();
                tickerEvents.forEach(pe -> pe.failInvestment("일괄 매수 실패: " + e.getMessage()));

                log.error("[Aggregator] Batch buy failed. userId={}, ticker={}, error={}",
                        userId, ticker, e.getMessage());
            }
        }
    }

    private void upsertPortfolio(User user, String ticker, int amount) {
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

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}