package com.tikkle.upbit.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.upbit.dto.response.UpbitRealtimePortfolioResponse;
import com.tikkle.upbit.service.UpbitPortfolioService;
import com.tikkle.upbit.swagger.UpbitPortfolioSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트엔드의 실시간 웹소켓 연동을 지원하기 위해 업비트 API와 직접 통신하는 포트폴리오 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/upbit/portfolios")
@RequiredArgsConstructor
public class UpbitPortfolioController implements UpbitPortfolioSwagger {
    private final UpbitPortfolioService upbitPortfolioService;

    @Override
    @GetMapping
    public ApiResponse<UpbitRealtimePortfolioResponse> getRealtimePortfolio(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UpbitRealtimePortfolioResponse response = upbitPortfolioService.getRealtimePortfolio(userDetails.getUserId());
        return ApiResponse.success(response);
    }
}