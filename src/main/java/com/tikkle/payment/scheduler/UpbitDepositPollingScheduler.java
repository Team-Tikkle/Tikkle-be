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
 * 업비트 원화 입금 내역을 폴링하여 매수 승인 상태를 업데이트하고 코인 매수를 체결하는 스케줄러입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitDepositPollingScheduler {
    private final PaymentEventRepository paymentEventRepository;
    private final UpbitDepositPollingProcessor depositPollingProcessor;

    @Scheduled(fixedDelay = 3000)
    public void pollDepositStatus() {
        // 트랜잭션 없이 조회
        List<PaymentEvent> pendingEvents = paymentEventRepository.findByStatus(PaymentStatus.PENDING_DEPOSIT);

        for (PaymentEvent event : pendingEvents) {
            try {
                depositPollingProcessor.processEvent(event.getId());
            } catch (Exception e) {
                log.error("[UpbitDepositPollingScheduler] 폴링루프 개별 이벤트 처리 중 예외 - eventId: {}", event.getId(), e);
            }
        }
    }
}