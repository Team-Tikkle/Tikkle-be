package com.tikkle.payment.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.tikkle.auth.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Payment History", description = "결제 내역 탭 API")
public interface PaymentHistorySwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "대시보드 조회", description = "특정 월(month)의 전체 결제 및 잔돈 통계 데이터를 반환합니다.")
    ApiResponse<PaymentDashboardResponse> getDashboard(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 월 (YYYY-MM)", example = "2026-06", required = true) String month
    );

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "결제 피드 내역 페이징 조회", description = "상태(status)와 월(month) 기반으로 결제 피드를 무한스크롤(Slice) 조회합니다.")
    ApiResponse<Slice<PaymentHistoryResponse>> getHistoryFeed(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 상태 필터 (ALL, PENDING, INVESTED, CANCELED)", example = "ALL", required = false) String status,
            @Parameter(description = "조회할 월 (YYYY-MM)", example = "2026-06", required = true) String month,
            @Parameter(hidden = true) Pageable pageable
    );
}