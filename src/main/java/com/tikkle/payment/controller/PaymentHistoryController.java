package com.tikkle.payment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import com.tikkle.payment.service.PaymentHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment History", description = "결제 내역 탭 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final PaymentHistoryService paymentHistoryService;

    @Operation(summary = "대시보드 조회", description = "특정 월(month)의 전체 결제 및 잔돈 통계 데이터를 반환합니다.")
    @GetMapping("/dashboard")
    public ApiResponse<PaymentDashboardResponse> getDashboard(
            @AuthenticationPrincipal String email,
            @Parameter(description = "조회할 월 (YYYY-MM)", example = "2026-06", required = true)
            @RequestParam String month
    ) {
        PaymentDashboardResponse response = paymentHistoryService.getDashboard(email, month);
        return ApiResponse.success(response);
    }

    @Operation(summary = "결제 피드 내역 페이징 조회", description = "상태(status)와 월(month) 기반으로 결제 피드를 무한스크롤(Slice) 조회합니다.")
    @GetMapping
    public ApiResponse<Slice<PaymentHistoryResponse>> getHistoryFeed(
            @AuthenticationPrincipal String email,
            @Parameter(description = "조회할 상태 필터 (ALL, PENDING, INVESTED, CANCELED)", example = "ALL", required = false)
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @Parameter(description = "조회할 월 (YYYY-MM)", example = "2026-06", required = true)
            @RequestParam String month,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Slice<PaymentHistoryResponse> response = paymentHistoryService.getHistoryFeed(email, status, month, pageable);
        return ApiResponse.success(response);
    }
}