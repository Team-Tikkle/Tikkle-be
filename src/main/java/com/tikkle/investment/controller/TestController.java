package com.tikkle.investment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.investment.service.AiPortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    private final AiPortfolioService aiPortfolioService;

    @PostMapping("/scheduler/portfolio")
    public ApiResponse<?> triggerPortfolioScheduler() {
        log.info("[Test API] triggerPortfolioScheduler called");
        aiPortfolioService.generateDailyTargets();
        return ApiResponse.successWithNoData();
    }
}