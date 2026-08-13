package com.tikkle.investment.scheduler;

import com.tikkle.investment.service.AiPortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
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
     * 9개 성향 조합을 순차 호출해 수 분이 걸리므로, {@code @Async}로 스케줄러 스레드를 즉시 반납합니다.
     * 그렇지 않으면 3초 주기 입금 폴링이 그동안 멈춥니다.
     */
    @Async
    @Scheduled(cron = "0 0 2,14 * * *", zone = "Asia/Seoul")
    public void scheduleDailyTargets() {
        log.info("[AiPortfolioScheduler] 12시간 주기 AI 매크로 유니버스 생성 스케줄러 실행");
        aiPortfolioService.generateMacroUniverses();
    }

    /**
     * 서버 부팅 시 최초 1회 즉시 후보군 풀 생성을 트리거하여 초기 공백 상태를 방지합니다.
     * 코인 메타데이터 동기화(CoinSyncScheduler)가 끝난 뒤 실행되어야 하므로 순서를 뒤로 지정합니다.
     * {@code @Async}로 실행해 수 분이 걸리는 AI 호출이 기동 스레드를 붙잡지 않게 합니다.
     */
    @Async
    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[AiPortfolioScheduler] 애플리케이션 초기화 완료. AI 매크로 유니버스 초기 생성 실행");
        aiPortfolioService.generateMacroUniverses();
    }
}