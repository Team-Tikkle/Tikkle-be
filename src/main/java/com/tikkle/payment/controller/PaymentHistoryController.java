package com.tikkle.payment.controller;

import com.tikkle.auth.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import com.tikkle.payment.service.PaymentHistoryService;
import com.tikkle.payment.swagger.PaymentHistorySwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentHistoryController implements PaymentHistorySwagger {

    private final PaymentHistoryService paymentHistoryService;

    @Override
    @GetMapping("/dashboard")
    public ApiResponse<PaymentDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String month
    ) {
        PaymentDashboardResponse response = paymentHistoryService.getDashboard(userDetails.getUsername(), month);
        return ApiResponse.success(response);
    }

    @Override
    @GetMapping
    public ApiResponse<Slice<PaymentHistoryResponse>> getHistoryFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam String month,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Slice<PaymentHistoryResponse> response = paymentHistoryService.getHistoryFeed(userDetails.getUsername(), status, month, pageable);
        return ApiResponse.success(response);
    }
}