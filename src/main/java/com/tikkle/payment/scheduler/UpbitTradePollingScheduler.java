package com.tikkle.payment.scheduler;

import com.tikkle.notification.service.PushNotificationService;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.client.UpbitOrderClient;
import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.upbit.service.UpbitPortfolioUpdater;
import com.tikkle.upbit.service.UpbitTradeService.TradeResult;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PENDING_TRADE 상태인 매수 주문을 백그라운드에서 추적하고 처리하는 스케줄러.
 * 10초마다 상태를 확인하며, 10분 이상 지연 시 주문을 강제 취소합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitTradePollingScheduler {
    private static final int TIMEOUT_MINUTES = 10;

    private final PaymentEventRepository paymentEventRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitOrderClient upbitOrderClient;
    private final UpbitPortfolioUpdater portfolioUpdater;
    private final PushNotificationService pushNotificationService;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void pollTradeStatus() {
        List<PaymentEvent> pendingEvents = paymentEventRepository.findByStatus(PaymentStatus.PENDING_TRADE);

        for (PaymentEvent event : pendingEvents) {
            try {
                LinkedAccount account = linkedAccountRepository.findByUserId(event.getUserId()).orElse(null);
                if (account == null) continue;

                String accessKey = account.getUpbitAccessKey();
                String secretKey = account.getUpbitSecretKey();

                // 1. 타임아웃 검사 (10분)
                LocalDateTime baseTime = event.getTradeRequestedAt() != null ? event.getTradeRequestedAt() : event.getCreatedAt();
                if (baseTime.plusMinutes(TIMEOUT_MINUTES).isBefore(LocalDateTime.now())) {
                    log.warn("[UpbitTradePollingScheduler] 매수 대기 타임아웃(10분) - eventId: {}, uuid: {}", event.getId(), event.getTradeUuid());
                    
                    // 주문 강제 취소
                    upbitOrderClient.cancelOrder(event.getTradeUuid(), accessKey, secretKey);
                    
                    event.failInvestment("시장 상황(유동성 부족 등)으로 매수 취소. 원화 환불 완료.");
                    
                    String title = "자동 매수 취소 안내";
                    String body = "시장 상황(상한가 묶임 등)으로 인해 주문이 10분간 체결되지 않아 자동 취소되었습니다. 묶여있던 원화는 고객님의 업비트 계좌로 안전하게 반환되었습니다.";
                    pushNotificationService.sendPush(event.getUserId(), title, body);
                    
                    continue;
                }

                // 2. 주문 체결 상태 조회
                log.info("[UpbitTradePollingScheduler] 업비트 주문 상태 조회 API 폴링 시작 - eventId: {}, tradeUuid: {}", event.getId(), event.getTradeUuid());
                UpbitOrderResponse orderDetails = upbitOrderClient.getOrderDetails(event.getTradeUuid(), accessKey, secretKey);
                String state = orderDetails.state();
                log.info("[UpbitTradePollingScheduler] 업비트 주문 상태 조회 API 응답 수신 - eventId: {}, state: {}", event.getId(), state);

                if ("done".equals(state)) {
                    log.info("[UpbitTradePollingScheduler] 지연 매수 체결 완료 - eventId: {}, uuid: {}", event.getId(), event.getTradeUuid());
                    
                    BigDecimal totalVolume = BigDecimal.ZERO;
                    BigDecimal totalFunds = BigDecimal.ZERO;

                    if (orderDetails.trades() != null) {
                        for (UpbitOrderResponse.UpbitTrade trade : orderDetails.trades()) {
                            totalVolume = totalVolume.add(new BigDecimal(trade.volume()));
                            totalFunds = totalFunds.add(new BigDecimal(trade.funds()));
                        }
                    }

                    if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal averagePrice = totalFunds.divide(totalVolume, 4, RoundingMode.HALF_UP);
                        
                        String targetMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : "KRW-BTC";
                        String coinName = event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : "코인";
                        
                        TradeResult result = new TradeResult(averagePrice, totalVolume, event.getTradeUuid(), false);
                        portfolioUpdater.updatePortfolio(event.getUserId(), targetMarket, result);
                        
                        event.completeInvestment(totalVolume, averagePrice);
                        
                        String title = coinName + " 매수 완료!";
                        String body = String.format("기다리셨죠? %s %s개 매수가 완료되어 포트폴리오에 반영되었습니다.", coinName, totalVolume.toPlainString());
                        pushNotificationService.sendPush(event.getUserId(), title, body);
                    }
                } else if ("cancel".equals(state)) {
                    log.warn("[UpbitTradePollingScheduler] 외부 요인으로 주문 취소됨 - eventId: {}, uuid: {}", event.getId(), event.getTradeUuid());
                    event.failInvestment("업비트에서 주문이 취소되었습니다.");
                    
                    String title = "매수 취소 안내";
                    String body = "업비트 거래소 사정으로 매수 주문이 취소되었습니다.";
                    pushNotificationService.sendPush(event.getUserId(), title, body);
                }
                // 'wait' 상태면 다음 폴링 주기에 다시 확인 (아무 작업 안 함)

            } catch (Exception e) {
                log.error("[UpbitTradePollingScheduler] 폴링 중 에러 발생 - eventId: {}", event.getId(), e);
            }
        }
    }
}
