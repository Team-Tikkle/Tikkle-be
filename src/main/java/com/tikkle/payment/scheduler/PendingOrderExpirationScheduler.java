package com.tikkle.payment.scheduler;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 24시간이 경과한 매수 대기 건을 만료 처리하는 스케줄러입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderExpirationScheduler {
    private final PaymentEventRepository paymentEventRepository;

    @Scheduled(cron = "0 0 * * * *") // 매시간 정각마다 실행
    @Transactional
    public void expireOldPendingOrders() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        List<PaymentEvent> expiredEvents = paymentEventRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING_PURCHASE, twentyFourHoursAgo);

        if (!expiredEvents.isEmpty()) {
            for (PaymentEvent event : expiredEvents) {
                event.skipInvestment("매수 승인 대기 시간(24시간) 초과로 인한 시스템 거절");
            }
            log.info("[PendingOrderExpirationScheduler] 만료된 매수 대기 건 NOT_INVESTED 처리 완료 - count: {}", expiredEvents.size());
        }
    }
}