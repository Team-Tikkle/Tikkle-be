package com.tikkle.investment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.investment.scheduler.AiPortfolioScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Profile("local")
public class TestController {
    private final AiPortfolioScheduler aiPortfolioScheduler;

    @PostMapping("/scheduler/portfolio")
    public ApiResponse<Void> triggerPortfolioScheduler() {
        log.info("[TestController] 포트폴리오 스케줄러 수동 실행 API 호출됨");
        aiPortfolioScheduler.scheduleDailyTargets();
        return ApiResponse.successWithNoData();
    }
}