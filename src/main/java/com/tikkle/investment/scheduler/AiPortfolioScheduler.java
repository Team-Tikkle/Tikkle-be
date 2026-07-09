package com.tikkle.investment.scheduler;

import com.tikkle.investment.service.AiPortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 AI 매크로 후보군 생성을 트리거하는 스케줄러 컴포넌트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPortfolioScheduler {
    private final AiPortfolioService aiPortfolioService;

    /**
     * 매일 자정과 정오(12시간 주기)에 15개 후보군 풀 생성을 트리거합니다.
     */
    @Scheduled(cron = "0 0 0,12 * * *", zone = "Asia/Seoul")
    public void scheduleDailyTargets() {
        log.info("[AiPortfolioScheduler] 12시간 주기 AI 매크로 유니버스 생성 스케줄러 실행");
        aiPortfolioService.generateMacroUniverses();
    }

    /**
     * 서버 부팅 시 최초 1회 즉시 후보군 풀 생성을 트리거하여 초기 공백 상태를 방지합니다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[AiPortfolioScheduler] 애플리케이션 초기화 완료. AI 매크로 유니버스 초기 생성 실행");
        aiPortfolioService.generateMacroUniverses();
    }
}