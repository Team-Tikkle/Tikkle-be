package com.tikkle.investment.scheduler;

import com.tikkle.investment.service.AiPortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiPortfolioScheduler {
    private final AiPortfolioService aiPortfolioService;

    // 매일 자정과 정오에 15개 후보군 풀 생성 (12시간 주기)
    @Scheduled(cron = "0 0 0,12 * * *", zone = "Asia/Seoul")
    public void scheduleDailyTargets() {
        log.info("[Scheduler] Triggering 12-hour AI Macro Universe generation...");
        aiPortfolioService.generateMacroUniverses();
    }

    // 서버 부팅 시 최초 1회 실행 보장
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[Event] Application ready. Triggering initial AI Macro Universe generation...");
        aiPortfolioService.generateMacroUniverses();
    }
}