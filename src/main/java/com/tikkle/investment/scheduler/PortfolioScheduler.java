package com.tikkle.investment.scheduler;

import com.tikkle.investment.service.AiPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortfolioScheduler {
    private final AiPortfolioService aiPortfolioService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void scheduleDailyPortfolioTargets() {
        aiPortfolioService.generateDailyTargets();
    }
}