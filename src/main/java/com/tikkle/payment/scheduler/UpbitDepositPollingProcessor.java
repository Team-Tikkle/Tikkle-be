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
        EventInfo info = null;
        try {
            info = self.getEventInfo(eventId);
            if (info == null) return;
            
            // 1. 타임아웃 검사 (3.5분)
            LocalDateTime baseTime = info.depositRequestedAt() != null ? info.depositRequestedAt() : info.createdAt();
            if (baseTime.plusSeconds(TIMEOUT_SECONDS).isBefore(LocalDateTime.now())) {
                log.warn("[UpbitDepositPollingProcessor] 입금 대기 타임아웃 - eventId: {}", eventId);
                self.handleTimeout(eventId);
                return;
            }

            if (info.account() == null) return;

            // 3. 입금 상태 조회 (외부 API - 트랜잭션 없음)
            log.info("[UpbitDepositPollingProcessor] 업비트 입금 상태 조회 API 폴링 시작 - eventId: {}, depositUuid: {}", eventId, info.depositUuid());
            UpbitDepositResponse response = upbitDepositClient.getDepositDetails(
                    info.depositUuid(),
                    info.account().getUpbitAccessKey(),
                    info.account().getUpbitSecretKey()
            );
            log.info("[UpbitDepositPollingProcessor] 업비트 입금 상태 조회 API 응답 수신 - eventId: {}, state: {}", eventId, response.state());

            sseConnectionManager.send(eventId, "PROCESSING", "업비트 2차 인증을 대기 중입니다.");

            // 4. 상태가 ACCEPTED 인 경우 매수 체결 진행
            if ("ACCEPTED".equalsIgnoreCase(response.state())) {
                log.info("[UpbitDepositPollingProcessor] 입금 완료 확인, 매수 진행 - eventId: {}", eventId);
                
                String targetMarket = info.targetMarket() != null ? info.targetMarket() : DEFAULT_FALLBACK_MARKET;
                String coinName = info.coinName() != null ? info.coinName() : "코인";
                var result = upbitTradeService.executeTrade(info.userId(), targetMarket, info.spareChange());
                
                self.handleTradeResult(eventId, result, coinName);
            } else if ("REJECTED".equalsIgnoreCase(response.state()) || "CANCELED".equalsIgnoreCase(response.state())) {
                log.warn("[UpbitDepositPollingProcessor] 입금 거절/취소 - eventId: {}, state: {}", eventId, response.state());
                self.handleDepositFailed(eventId);
            }

        } catch (UpbitInvalidKeyException e) {
            log.error("[UpbitDepositPollingProcessor] 업비트 인증 키 만료/권한 없음 - eventId: {}", eventId);
            if (info != null) {
                self.handleInvalidKeyError(eventId, info.userId());
            } else {
                self.handleGeneralError(eventId, "업비트 인증 키가 만료되거나 권한이 없습니다.");
            }
        } catch (UpbitOrderFailedException e) {
            log.error("[UpbitDepositPollingProcessor] 업비트 매수 주문 실패 - eventId: {}", eventId, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "업비트 매수 주문이 거절되거나 취소되었습니다.";
            self.handleTradeFailed(eventId, errorMessage);
        } catch (Exception e) {
            log.error("[UpbitDepositPollingProcessor] 폴링 중 에러 발생 - eventId: {}", eventId, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "자동 매수 중 알 수 없는 에러가 발생했습니다.";
            self.handleGeneralError(eventId, errorMessage);
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
            event.revertToPendingPurchase("업비트 2차 인증 시간(3분) 초과");
        }
        sseConnectionManager.send(eventId, "TIMEOUT", "업비트 2차 인증 시간이 초과되었습니다.");
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
                    "message", "주문이 정상적으로 접수되었습니다. 거래소 사정에 따라 체결이 지연되고 있으며, 체결이 완료되면 푸시 알림으로 알려드리겠습니다."
            );
            sseConnectionManager.send(eventId, "PENDING_TRADE", pendingData);
            sseConnectionManager.complete(eventId);
        } else {
            event.completeInvestment(result.executedVolume(), result.executedPrice());
            
            String successMsg = String.format("%s %s개 매수 성공했습니다.", coinName, result.executedVolume().toPlainString());
            Map<String, Object> successData = Map.of(
                    "status", "SUCCESS",
                    "message", successMsg,
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
        orderApprovalService.markAsFailed(eventId, "업비트 입금 거절 또는 취소");
        Map<String, Object> failData = Map.of(
                "status", "DEPOSIT_FAILED",
                "message", "업비트 입금이 거절되거나 취소되었습니다."
        );
        sseConnectionManager.send(eventId, "DEPOSIT_FAILED", failData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleTradeFailed(Long eventId, String errorMessage) {
        orderApprovalService.markAsFailed(eventId, "매수 체결 실패: " + errorMessage);
        Map<String, Object> errorData = Map.of(
                "status", "TRADE_FAILED",
                "message", "업비트 매수 주문이 거절되거나 취소되었습니다."
        );
        sseConnectionManager.send(eventId, "TRADE_FAILED", errorData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleGeneralError(Long eventId, String errorMessage) {
        orderApprovalService.markAsFailed(eventId, "매수 체결 실패: " + errorMessage);
        Map<String, Object> errorData = Map.of(
                "status", "FAILED",
                "message", "자동 매수 중 알 수 없는 에러가 발생했습니다."
        );
        sseConnectionManager.send(eventId, "FAILED", errorData);
        sseConnectionManager.complete(eventId);
    }

    @Transactional
    public void handleInvalidKeyError(Long eventId, Long userId) {
        LinkedAccount account = linkedAccountRepository.findByUserId(userId).orElse(null);
        if (account != null) {
            account.invalidateUpbitKey();
        }
        orderApprovalService.markAsFailed(eventId, "업비트 인증 키 만료/권한 없음");
        Map<String, Object> errorData = Map.of(
                "status", "UPBIT_INVALID_KEY",
                "message", "업비트 인증 키가 만료되거나 권한이 없습니다. 앱에서 업비트 계정을 다시 연동해주세요."
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