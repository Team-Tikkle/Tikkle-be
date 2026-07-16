package com.tikkle.payment.scheduler;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.client.UpbitOrderClient;
import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.upbit.service.UpbitPortfolioUpdater;
import com.tikkle.upbit.service.UpbitTradeService.TradeResult;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 업비트 매수 주문 상태를 폴링하고 체결 결과를 처리하는 프로세서입니다.
 * 외부 API 연동 시 발생하는 트랜잭션 병목을 방지하기 위해 
 * 데이터 갱신 로직만 별도의 트랜잭션으로 격리하여 실행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitTradePollingProcessor {
    private static final int TIMEOUT_MINUTES = 10;

    @Lazy
    @Autowired
    private UpbitTradePollingProcessor self;

    private final PaymentEventRepository paymentEventRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitOrderClient upbitOrderClient;
    private final UpbitPortfolioUpdater portfolioUpdater;
    private final com.tikkle.payment.sse.SseConnectionManager sseConnectionManager;

    /**
     * 개별 이벤트에 대해 업비트 API를 호출하고 결과를 처리합니다.
     * 트랜잭션은 API 호출 외부(또는 분리된 메서드)에서 관리되도록 하여 DB 락을 방지합니다.
     */
    public void processEvent(Long eventId) {
        // 1. 필요한 기본 정보만 트랜잭션 내에서 조회 (API 호출 전)
        EventInfo info = self.getEventInfo(eventId);
        if (info == null) return;
        
        if (info.account() == null) {
            log.warn("[UpbitTradePollingProcessor] LinkedAccount가 존재하지 않습니다 - eventId: {}, userId: {}", eventId, info.userId());
            return;
        }

        String accessKey = info.account().getUpbitAccessKey();
        String secretKey = info.account().getUpbitSecretKey();

        // 2. 타임아웃 검사 (10분) - 외부 API(취소) 호출 포함
        LocalDateTime baseTime = info.tradeRequestedAt() != null ? info.tradeRequestedAt() : info.createdAt();
        if (baseTime.plusMinutes(TIMEOUT_MINUTES).isBefore(LocalDateTime.now())) {
            log.warn("[UpbitTradePollingProcessor] 매수 대기 타임아웃(10분) - eventId: {}, uuid: {}", eventId, info.tradeUuid());
            
            try {
                upbitOrderClient.cancelOrder(info.tradeUuid(), accessKey, secretKey);
            } catch (UpbitInvalidKeyException e) {
                log.error("[UpbitTradePollingProcessor] 주문 취소 중 업비트 인증 키 만료/권한 없음 - eventId: {}", eventId);
                self.handleInvalidKeyError(eventId);
                return;
            } catch (Exception e) {
                log.error("[UpbitTradePollingProcessor] 주문 취소 API 실패 - eventId: {}", eventId, e);
            }
            
            self.handleTimeout(eventId, info.userId());
            return;
        }

        // 3. 외부 API 호출 (트랜잭션 없음)
        log.info("[UpbitTradePollingProcessor] 업비트 주문 상태 조회 API 폴링 시작 - eventId: {}, tradeUuid: {}", eventId, info.tradeUuid());
        UpbitOrderResponse orderDetails;
        try {
            orderDetails = upbitOrderClient.getOrderDetails(info.tradeUuid(), accessKey, secretKey);
        } catch (UpbitInvalidKeyException e) {
            log.error("[UpbitTradePollingProcessor] 주문 상태 조회 중 업비트 인증 키 만료/권한 없음 - eventId: {}", eventId);
            self.handleInvalidKeyError(eventId);
            return;
        } catch (Exception e) {
            log.error("[UpbitTradePollingProcessor] 주문 상태 조회 실패 - eventId: {}", eventId, e);
            return;
        }
        
        String state = orderDetails.state();
        log.info("[UpbitTradePollingProcessor] 업비트 주문 상태 조회 API 응답 수신 - eventId: {}, state: {}", eventId, state);

        // 4. 상태에 따른 DB 업데이트 및 푸시 알림 (DB 쓰기는 트랜잭션 내에서)
        if ("done".equals(state)) {
            self.handleDoneOrder(eventId, info, orderDetails);
        } else if ("cancel".equals(state)) {
            self.handleCanceledOrder(eventId, info.userId(), info.tradeUuid());
        }
    }

    @Transactional(readOnly = true)
    public EventInfo getEventInfo(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event == null) return null;
        
        LinkedAccount account = linkedAccountRepository.findByUserId(event.getUserId()).orElse(null);
        
        String targetMarket = null;
        String coinName = null;
        if (event.getTargetCoin() != null) {
            targetMarket = event.getTargetCoin().getMarket();
            coinName = event.getTargetCoin().getKoreanName();
        }

        return new EventInfo(
            event.getUserId(),
            event.getTradeUuid(),
            event.getTradeRequestedAt(),
            event.getCreatedAt(),
            account,
            targetMarket,
            coinName
        );
    }

    @Transactional
    public void handleTimeout(Long eventId, Long userId) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event != null) {
            event.failInvestment("시장 상황(유동성 부족 등)으로 매수 취소. 원화는 업비트 계좌에 보관됩니다.");
        }
    }

    @Transactional
    public void handleDoneOrder(Long eventId, EventInfo info, UpbitOrderResponse orderDetails) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        if (info.targetMarket() == null || info.coinName() == null) {
            log.error("[UpbitTradePollingProcessor] targetCoin 정보 누락 - eventId: {}", eventId);
            event.failInvestment("매수 대상 코인 정보가 누락되었습니다.");
            return;
        }

        log.info("[UpbitTradePollingProcessor] 지연 매수 체결 완료 처리 시작 - eventId: {}, uuid: {}", eventId, info.tradeUuid());
        
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalFunds = BigDecimal.ZERO;

        if (orderDetails.trades() != null) {
            for (UpbitOrderResponse.UpbitTrade trade : orderDetails.trades()) {
                String volStr = trade.volume() != null ? trade.volume() : "0";
                String fundsStr = trade.funds() != null ? trade.funds() : "0";
                totalVolume = totalVolume.add(new BigDecimal(volStr));
                totalFunds = totalFunds.add(new BigDecimal(fundsStr));
            }
        }

        if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal averagePrice = totalFunds.divide(totalVolume, 4, RoundingMode.HALF_UP);
            
            TradeResult result = new TradeResult(averagePrice, totalVolume, info.tradeUuid(), false);
            portfolioUpdater.updatePortfolio(info.userId(), info.targetMarket(), result);
            
            event.completeInvestment(totalVolume, averagePrice);

            String successMsg = String.format("%s %s개 매수 성공했습니다.", info.coinName(), result.executedVolume().toPlainString());
            java.util.Map<String, Object> successData = java.util.Map.of(
                    "status", "SUCCESS",
                    "message", successMsg,
                    "targetCoinName", info.coinName(),
                    "investedVolume", result.executedVolume(),
                    "investedPrice", result.executedPrice()
            );
            sseConnectionManager.send(eventId, "SUCCESS", successData);
            sseConnectionManager.complete(eventId);
        } else {
            log.warn("[UpbitTradePollingProcessor] 체결 상태는 done 이나 체결량이 0 - eventId: {}", eventId);
            event.failInvestment("주문이 완료되었으나 실제 체결된 코인 수량이 0입니다.");
        }
    }

    @Transactional
    public void handleCanceledOrder(Long eventId, Long userId, String tradeUuid) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        log.warn("[UpbitTradePollingProcessor] 외부 요인으로 주문 취소됨 - eventId: {}, uuid: {}", eventId, tradeUuid);
        event.failInvestment("업비트에서 주문이 취소되었습니다.");
    }

    @Transactional
    public void handleInvalidKeyError(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event != null) {
            event.failInvestment("업비트 인증 키가 만료되거나 권한이 없습니다.");
        }
    }

    public record EventInfo(
        Long userId,
        String tradeUuid,
        LocalDateTime tradeRequestedAt,
        LocalDateTime createdAt,
        LinkedAccount account,
        String targetMarket,
        String coinName
    ) {}
}