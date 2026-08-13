package com.tikkle.payment.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.response.InProgressPaymentResponse;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

import com.tikkle.payment.dto.request.CategoryUpdateRequest;
import com.tikkle.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

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

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "진행 중인 결제 건 조회",
            description = "매수 승인 이후 아직 끝나지 않은 건(업비트 입금 대기 PENDING_DEPOSIT, 체결 대기 PENDING_TRADE)을 최신순으로 반환합니다. "
                    + "2차 인증을 위해 앱을 벗어났다 돌아왔을 때 진행 중인 화면을 복구하고, 반환된 eventId로 SSE를 재구독하는 용도입니다. "
                    + "진행 중인 건이 없으면 빈 배열을 반환합니다.")
    ApiResponse<List<InProgressPaymentResponse>> getInProgressPayments(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "결제 카테고리 수정", description = "특정 결제 건의 카테고리를 사용자가 직접 변경합니다.")
    ApiResponse<Void> updateCategory(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "결제 이벤트 ID", example = "10293", required = true) @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request
    );
}