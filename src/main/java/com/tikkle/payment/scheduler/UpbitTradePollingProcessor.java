package com.tikkle.payment.scheduler;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.sse.SseConnectionManager;
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
import java.util.Map;

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
    private final SseConnectionManager sseConnectionManager;

    /**
     * 개별 이벤트에 대해 업비트 API를 호출하고 결과를 처리합니다.
     * 트랜잭션은 API 호출 외부(또는 분리된 메서드)에서 관리되도록 하여 DB 락을 방지합니다.
     */
    public void processEvent(Long eventId) {
        EventInfo info = self.getEventInfo(eventId);
        if (info == null) return;

        if (info.account() == null) {
            log.warn("[UpbitTradePollingProcessor] LinkedAccount가 존재하지 않습니다 - eventId: {}, userId: {}", eventId, info.userId());
            return;
        }

        String accessKey = info.account().getUpbitAccessKey();
        String secretKey = info.account().getUpbitSecretKey();

        // 1. 타임아웃 검사 (10분) - 외부 API(취소) 호출 포함
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

            self.handleTimeout(eventId);
            return;
        }

        // 2. 외부 API 호출 (트랜잭션 없음)
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

        // 3. 시장가 매수는 부분 체결 후 잔량이 cancel 되는 것이 정상 흐름이므로 done/cancel 모두 체결 확인 대상이다
        if ("done".equals(state) || "cancel".equals(state)) {
            self.handleSettledOrder(eventId, info, orderDetails, state);
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
    public void handleTimeout(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event != null) {
            event.failInvestment("매수 주문 10분 타임아웃으로 주문 취소. 입금된 원화는 업비트 계좌에 잔류");
        }
    }

    /**
     * 체결이 종료된(done/cancel) 주문의 실제 체결량을 확인해 투자 완료 또는 실패로 확정합니다.
     * cancel 이어도 부분 체결분이 있으면 코인이 실제로 매수된 것이므로 반드시 원장에 반영해야 합니다.
     */
    @Transactional
    public void handleSettledOrder(Long eventId, EventInfo info, UpbitOrderResponse orderDetails, String state) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        if (info.targetMarket() == null || info.coinName() == null) {
            log.error("[UpbitTradePollingProcessor] targetCoin 정보 누락 - eventId: {}", eventId);
            event.failInvestment("targetCoin 정보 누락으로 체결 반영 불가");
            return;
        }

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

        if (totalVolume.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("[UpbitTradePollingProcessor] 주문 종료(state={}) 이나 체결 수량 0 - eventId: {}", state, eventId);
            event.failInvestment("주문 state=" + state + " 이나 체결 수량 0. 입금된 원화는 업비트 계좌에 잔류");
            return;
        }

        log.info("[UpbitTradePollingProcessor] 지연 매수 체결 완료 처리 시작 - eventId: {}, state: {}, uuid: {}", eventId, state, info.tradeUuid());

        BigDecimal averagePrice = totalFunds.divide(totalVolume, 4, RoundingMode.HALF_UP);
        TradeResult result = new TradeResult(averagePrice, totalVolume, info.tradeUuid(), false);

        portfolioUpdater.updatePortfolio(info.userId(), info.targetMarket(), result);
        event.completeInvestment(totalVolume, averagePrice);

        Map<String, Object> successData = Map.of(
                "status", "SUCCESS",
                "message", "지연 체결 완료(state=" + state + ")",
                "targetCoinName", info.coinName(),
                "investedVolume", result.executedVolume(),
                "investedPrice", result.executedPrice()
        );
        sseConnectionManager.send(eventId, "SUCCESS", successData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleInvalidKeyError(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event != null) {
            event.failInvestment("업비트 API 키 만료 또는 권한 부족(401/403)");
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
