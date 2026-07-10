package com.tikkle.payment.scheduler;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PENDING_TRADE 상태인 매수 주문을 백그라운드에서 추적하고 처리하는 스케줄러.
 * 10초마다 상태를 확인하며, 10분 이상 지연 시 주문을 강제 취소합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitTradePollingScheduler {

    private final PaymentEventRepository paymentEventRepository;
    private final UpbitTradePollingProcessor tradePollingProcessor;

    @Scheduled(fixedDelay = 10000)
    public void pollTradeStatus() {
        // 읽기 전용으로 상태만 가져오므로, 트랜잭션 없이 List 조회
        List<PaymentEvent> pendingEvents = paymentEventRepository.findByStatus(PaymentStatus.PENDING_TRADE);

        for (PaymentEvent event : pendingEvents) {
            try {
                tradePollingProcessor.processEvent(event.getId());
            } catch (Exception e) {
                log.error("[UpbitTradePollingScheduler] 폴링 중 예외 발생 - eventId: {}", event.getId(), e);
            }
        }
    }
}