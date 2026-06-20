package com.tikkle.investment.scheduler;

import com.tikkle.investment.service.AiPortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioScheduler {
    private final AiPortfolioService aiPortfolioService;
    private final StringRedisTemplate redisTemplate;

    @Schedules({
            @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul"),
            @Scheduled(cron = "0 30 11 * * *", zone = "Asia/Seoul"),
            @Scheduled(cron = "0 30 14 * * *", zone = "Asia/Seoul")
    })
    public void scheduleDailyPortfolioTargets() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        // 분 단위까지만 키로 사용하여 1분 이내에 실행되는 클러스터 노드들 간의 중복 실행 방지
        String timeKey = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String lockKey = "scheduler:lock:portfolio:target:" + timeKey;

        // 분산 락 획득 시도 (TTL 10분)
        Boolean isLockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(isLockAcquired)) {
            log.info("[Scheduler Lock Acquired] Starting scheduleDailyPortfolioTargets at {}", now);
            aiPortfolioService.generateDailyTargets();
        } else {
            log.info("[Scheduler Lock Denied] Another instance is already running scheduleDailyPortfolioTargets for {}", timeKey);
        }
    }
}