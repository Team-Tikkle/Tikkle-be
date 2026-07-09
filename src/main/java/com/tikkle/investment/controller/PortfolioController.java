package com.tikkle.investment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.investment.dto.response.PortfolioResponse;
import com.tikkle.investment.service.PortfolioService;
import com.tikkle.investment.swagger.PortfolioSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 포트폴리오(홈 화면) 조회를 담당하는 웹 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController implements PortfolioSwagger {
    private final PortfolioService portfolioService;

    @Override
    @GetMapping
    public ApiResponse<PortfolioResponse> getPortfolio(@AuthenticationPrincipal CustomUserDetails userDetails) {
        PortfolioResponse response = portfolioService.getPortfolio(userDetails.getUserId());
        return ApiResponse.success(response);
    }
}