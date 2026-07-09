package com.tikkle.payment.scheduler;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.service.OrderApprovalService;
import com.tikkle.payment.sse.SseConnectionManager;
import com.tikkle.upbit.client.UpbitDepositClient;
import com.tikkle.upbit.dto.response.UpbitDepositResponse;
import com.tikkle.upbit.service.UpbitTradeService;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 업비트 원화 입금 내역을 폴링하여 매수 승인 상태를 업데이트하고 코인 매수를 체결하는 스케줄러입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitDepositPollingScheduler {
    private static final String DEFAULT_FALLBACK_MARKET = "KRW-BTC";
    private static final int TIMEOUT_MINUTES = 3;

    private final PaymentEventRepository paymentEventRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitDepositClient upbitDepositClient;
    private final UpbitTradeService upbitTradeService;
    private final OrderApprovalService orderApprovalService;
    private final SseConnectionManager sseConnectionManager;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void pollDepositStatus() {
        List<PaymentEvent> pendingEvents = paymentEventRepository.findByStatus(PaymentStatus.PENDING_DEPOSIT);

        for (PaymentEvent event : pendingEvents) {
            try {
                // 1. 타임아웃 검사 (3분)
                LocalDateTime baseTime = event.getDepositRequestedAt() != null ? event.getDepositRequestedAt() : event.getCreatedAt();
                if (baseTime.plusMinutes(TIMEOUT_MINUTES).isBefore(LocalDateTime.now())) {
                    log.warn("[UpbitDepositPollingScheduler] 입금 대기 타임아웃 - eventId: {}", event.getId());
                    event.revertToPendingPurchase("업비트 2차 인증 시간(3분) 초과");
                    sseConnectionManager.send(event.getId(), "TIMEOUT", "업비트 2차 인증 시간이 초과되었습니다.");
                    sseConnectionManager.complete(event.getId());
                    continue;
                }

                // 2. 업비트 키 조회
                LinkedAccount account = linkedAccountRepository.findByUserId(event.getUserId()).orElse(null);
                if (account == null) continue;

                // 3. 입금 상태 조회
                log.info("[UpbitDepositPollingScheduler] 업비트 입금 상태 조회 API 폴링 시작 - eventId: {}, depositUuid: {}", event.getId(), event.getDepositUuid());
                UpbitDepositResponse response = upbitDepositClient.getDepositDetails(
                        event.getDepositUuid(),
                        account.getUpbitAccessKey(),
                        account.getUpbitSecretKey()
                );
                log.info("[UpbitDepositPollingScheduler] 업비트 입금 상태 조회 API 응답 수신 - eventId: {}, state: {}", event.getId(), response.state());

                sseConnectionManager.send(event.getId(), "PROCESSING", "업비트 2차 인증을 대기 중입니다.");

                // 4. 상태가 ACCEPTED 인 경우 매수 체결 진행
                if ("ACCEPTED".equalsIgnoreCase(response.state())) {
                    log.info("[UpbitDepositPollingScheduler] 입금 완료 확인, 매수 진행 - eventId: {}", event.getId());
                    
                    String targetMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : DEFAULT_FALLBACK_MARKET;
                    String coinName = event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : "코인";
                    var result = upbitTradeService.executeTrade(event.getUserId(), targetMarket, event.getSpareChange());
                    
                    if (result.isPending()) {
                        event.updateToPendingTrade(result.tradeUuid());
                        Map<String, Object> pendingData = Map.of(
                                "status", "PENDING_TRADE",
                                "message", "주문이 정상적으로 접수되었습니다. 거래소 사정에 따라 체결이 지연되고 있으며, 체결이 완료되면 푸시 알림으로 알려드리겠습니다."
                        );
                        sseConnectionManager.send(event.getId(), "PENDING_TRADE", pendingData);
                        sseConnectionManager.complete(event.getId());
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
                        sseConnectionManager.send(event.getId(), "SUCCESS", successData);
                        sseConnectionManager.complete(event.getId());
                    }
                } else if ("REJECTED".equalsIgnoreCase(response.state()) || "CANCELED".equalsIgnoreCase(response.state())) {
                    log.warn("[UpbitDepositPollingScheduler] 입금 거절/취소 - eventId: {}, state: {}", event.getId(), response.state());
                    orderApprovalService.markAsFailed(event.getId(), "업비트 입금 거절 또는 취소");
                    
                    Map<String, Object> failData = Map.of(
                            "status", "FAILED",
                            "message", "업비트 입금이 거절되거나 취소되었습니다."
                    );
                    sseConnectionManager.send(event.getId(), "FAILED", failData);
                    sseConnectionManager.complete(event.getId());
                }

            } catch (Exception e) {
                log.error("[UpbitDepositPollingScheduler] 폴링 중 에러 발생 - eventId: {}", event.getId(), e);
                String errorMessage = e.getMessage() != null ? e.getMessage() : "자동 매수 중 알 수 없는 에러가 발생했습니다.";
                orderApprovalService.markAsFailed(event.getId(), "매수 체결 실패: " + errorMessage);
                
                Map<String, Object> errorData = Map.of(
                        "status", "FAILED",
                        "message", "업비트 매수 주문이 거절되거나 취소되었습니다."
                );
                sseConnectionManager.send(event.getId(), "FAILED", errorData);
                sseConnectionManager.complete(event.getId());
            }
        }
    }
}