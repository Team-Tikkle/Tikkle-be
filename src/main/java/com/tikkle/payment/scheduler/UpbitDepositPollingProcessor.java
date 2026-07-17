package com.tikkle.payment.scheduler;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.service.OrderApprovalService;
import com.tikkle.payment.sse.SseConnectionManager;
import com.tikkle.upbit.client.UpbitDepositClient;
import com.tikkle.upbit.dto.response.UpbitDepositResponse;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.upbit.exception.UpbitOrderFailedException;
import com.tikkle.upbit.service.UpbitTradeService;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 업비트 원화 입금 내역을 폴링하여 승인 상태를 업데이트하고 코인 매수를 체결하는 프로세서입니다.
 * 외부 API(입금 내역 조회) 연동 시 발생하는 트랜잭션 병목을 방지하기 위해
 * 데이터 갱신 로직만 별도의 트랜잭션으로 격리하여 실행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitDepositPollingProcessor {
    private static final String DEFAULT_FALLBACK_MARKET = "KRW-BTC";
    private static final int TIMEOUT_SECONDS = 210;

    @Lazy
    @Autowired
    private UpbitDepositPollingProcessor self;

    private final PaymentEventRepository paymentEventRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitDepositClient upbitDepositClient;
    private final UpbitTradeService upbitTradeService;
    private final OrderApprovalService orderApprovalService;
    private final SseConnectionManager sseConnectionManager;

    public void processEvent(Long eventId) {
        try {
            EventInfo info = self.getEventInfo(eventId);
            if (info == null) return;

            // 1. 타임아웃 검사 (210초)
            LocalDateTime baseTime = info.depositRequestedAt() != null ? info.depositRequestedAt() : info.createdAt();
            if (baseTime.plusSeconds(TIMEOUT_SECONDS).isBefore(LocalDateTime.now())) {
                log.warn("[UpbitDepositPollingProcessor] 입금 대기 타임아웃 - eventId: {}", eventId);
                self.handleTimeout(eventId);
                return;
            }

            if (info.account() == null) return;

            // 2. 입금 상태 조회 (외부 API - 트랜잭션 없음)
            log.info("[UpbitDepositPollingProcessor] 업비트 입금 상태 조회 API 폴링 시작 - eventId: {}, depositUuid: {}", eventId, info.depositUuid());
            UpbitDepositResponse response = upbitDepositClient.getDepositDetails(
                    info.depositUuid(),
                    info.account().getUpbitAccessKey(),
                    info.account().getUpbitSecretKey()
            );
            log.info("[UpbitDepositPollingProcessor] 업비트 입금 상태 조회 API 응답 수신 - eventId: {}, state: {}", eventId, response.state());

            // 3. 상태가 ACCEPTED 인 경우 매수 체결 진행
            if ("ACCEPTED".equalsIgnoreCase(response.state())) {
                log.info("[UpbitDepositPollingProcessor] 입금 완료 확인, 매수 진행 - eventId: {}", eventId);

                String targetMarket = info.targetMarket() != null ? info.targetMarket() : DEFAULT_FALLBACK_MARKET;
                String coinName = info.coinName() != null ? info.coinName() : "코인";
                var result = upbitTradeService.executeTrade(info.userId(), targetMarket, info.spareChange(), buildOrderIdentifier(eventId));

                self.handleTradeResult(eventId, result, coinName);
            } else if ("REJECTED".equalsIgnoreCase(response.state()) || "CANCELED".equalsIgnoreCase(response.state())) {
                log.warn("[UpbitDepositPollingProcessor] 입금 거절/취소 - eventId: {}, state: {}", eventId, response.state());
                self.handleDepositFailed(eventId);
            } else {
                sseConnectionManager.send(eventId, "PROCESSING", "업비트 입금 2차 인증 대기 중");
            }

        } catch (UpbitInvalidKeyException e) {
            log.error("[UpbitDepositPollingProcessor] 업비트 인증 키 만료/권한 없음 - eventId: {}", eventId);
            self.handleInvalidKeyError(eventId);
        } catch (UpbitOrderFailedException e) {
            log.error("[UpbitDepositPollingProcessor] 업비트 매수 주문 실패 - eventId: {}", eventId, e);
            self.handleTradeFailed(eventId);
        } catch (Exception e) {
            log.error("[UpbitDepositPollingProcessor] 폴링 중 에러 발생 - eventId: {}", eventId, e);
            self.handleGeneralError(eventId, e.getMessage());
        }
    }

    /**
     * 결제 이벤트당 고정된 주문 멱등 식별자를 만듭니다.
     * 동일 결제 건에 대한 재시도가 업비트에 중복 주문으로 접수되지 않도록 보장합니다.
     */
    private String buildOrderIdentifier(Long eventId) {
        return "tikkle-" + eventId;
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
            event.getDepositUuid(),
            event.getDepositRequestedAt(),
            event.getCreatedAt(),
            account,
            targetMarket,
            coinName,
            event.getSpareChange()
        );
    }

    @Transactional
    public void handleTimeout(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event != null) {
            event.revertToPendingPurchase("업비트 입금 2차 인증 미완료로 타임아웃(210초). PENDING_PURCHASE로 복구");
        }
        sseConnectionManager.send(eventId, "TIMEOUT", "업비트 2차 인증 미완료로 입금 대기 타임아웃(210초). 결제 건은 PENDING_PURCHASE로 복구되어 재승인 가능");
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleTradeResult(Long eventId, UpbitTradeService.TradeResult result, String coinName) {
        PaymentEvent event = paymentEventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        if (result.isPending()) {
            event.updateToPendingTrade(result.tradeUuid());
            Map<String, Object> pendingData = Map.of(
                    "status", "PENDING_TRADE",
                    "message", "주문 접수됨. 5초 내 체결이 확인되지 않아 비동기 추적으로 전환. 스트림 종료되며 최종 결과는 결제 내역 재조회로 확인 필요"
            );
            sseConnectionManager.send(eventId, "PENDING_TRADE", pendingData);
            sseConnectionManager.complete(eventId);
        } else {
            event.completeInvestment(result.executedVolume(), result.executedPrice());

            Map<String, Object> successData = Map.of(
                    "status", "SUCCESS",
                    "message", "시장가 매수 체결 완료",
                    "targetCoinName", coinName,
                    "investedVolume", result.executedVolume(),
                    "investedPrice", result.executedPrice()
            );
            sseConnectionManager.send(eventId, "SUCCESS", successData);
            sseConnectionManager.complete(eventId);
        }
    }

    @Transactional
    public void handleDepositFailed(Long eventId) {
        orderApprovalService.markAsFailed(eventId, "업비트 입금 거절 또는 취소(deposit state=REJECTED|CANCELED)");
        Map<String, Object> failData = Map.of(
                "status", "DEPOSIT_FAILED",
                "message", "업비트 원화 입금이 거절 또는 취소됨. 출금된 원화 없음"
        );
        sseConnectionManager.send(eventId, "DEPOSIT_FAILED", failData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleTradeFailed(Long eventId) {
        orderApprovalService.markAsFailed(eventId, "업비트 매수 주문 접수 실패. 입금된 원화는 업비트 계좌에 잔류");
        Map<String, Object> errorData = Map.of(
                "status", "TRADE_FAILED",
                "message", "업비트 매수 주문 접수 실패. 입금된 원화는 업비트 계좌에 잔류"
        );
        sseConnectionManager.send(eventId, "TRADE_FAILED", errorData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleGeneralError(Long eventId, String errorMessage) {
        orderApprovalService.markAsFailed(eventId, "매수 처리 중 예기치 못한 오류: " + errorMessage);
        Map<String, Object> errorData = Map.of(
                "status", "FAILED",
                "message", "매수 처리 중 예기치 못한 서버 오류"
        );
        sseConnectionManager.send(eventId, "FAILED", errorData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleInvalidKeyError(Long eventId) {
        orderApprovalService.markAsFailed(eventId, "업비트 API 키 만료 또는 권한 부족(401/403)");
        Map<String, Object> errorData = Map.of(
                "status", "UPBIT_INVALID_KEY",
                "message", "업비트 API 키 만료 또는 권한 부족(401/403). 재연동 필요"
        );
        sseConnectionManager.send(eventId, "UPBIT_INVALID_KEY", errorData);
        sseConnectionManager.complete(eventId);
    }

    public record EventInfo(
        Long userId,
        String depositUuid,
        LocalDateTime depositRequestedAt,
        LocalDateTime createdAt,
        LinkedAccount account,
        String targetMarket,
        String coinName,
        Integer spareChange
    ) {}
}
