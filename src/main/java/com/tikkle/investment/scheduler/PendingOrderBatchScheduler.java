package com.tikkle.investment.scheduler;

import com.tikkle.investment.service.PendingOrderAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 장외 대기 건(PENDING) 일괄 매수 배치 스케줄러.
 * 매일 평일 아침 09:00에 동작하여, 전날 장외 시간에 쌓인 잔돈 매수 건을 합산 후 일괄 매수합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderBatchScheduler {
    private final PendingOrderAggregator pendingOrderAggregator;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    public void schedulePendingOrderBatch() {
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        String lockKey = "scheduler:lock:pending-order-batch:" + today;

        // 분산 락 획득 시도 (TTL 30분 — 배치 처리 시간 고려)
        Boolean isLockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofMinutes(30));

        if (Boolean.TRUE.equals(isLockAcquired)) {
            log.info("[PendingOrderBatch] Lock acquired. Starting batch for {}", today);
            pendingOrderAggregator.processAllPendingOrders();
            log.info("[PendingOrderBatch] Batch completed for {}", today);
        } else {
            log.info("[PendingOrderBatch] Lock denied. Another instance is processing for {}", today);
        }
    }
}