package com.tikkle.investment.controller;

import com.tikkle.auth.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.investment.dto.response.PortfolioResponse;
import com.tikkle.investment.service.PortfolioService;
import com.tikkle.investment.swagger.PortfolioSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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