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

/**
 * 결제 내역 조회 및 대시보드 관련 API 엔드포인트를 제공하는 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentHistoryController implements PaymentHistorySwagger {

    private final PaymentHistoryService paymentHistoryService;

    /**
     * 특정 월의 전체 결제 및 잔돈 통계 대시보드를 조회합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param month 조회할 월 (YYYY-MM)
     * @return 대시보드 통계 응답
     */
    @Override
    @GetMapping("/dashboard")
    public ApiResponse<PaymentDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String month
    ) {
        PaymentDashboardResponse response = paymentHistoryService.getDashboard(userDetails.getUserId(), month);
        return ApiResponse.success(response);
    }

    /**
     * 상태와 월 기반으로 결제 피드를 무한스크롤 페이징 조회합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param status 조회할 상태 필터 (ALL, PENDING, INVESTED, CANCELED)
     * @param month 조회할 월 (YYYY-MM)
     * @param pageable 페이징 정보
     * @return 결제 피드 응답 (Slice)
     */
    @Override
    @GetMapping
    public ApiResponse<Slice<PaymentHistoryResponse>> getHistoryFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam String month,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Slice<PaymentHistoryResponse> response = paymentHistoryService.getHistoryFeed(userDetails.getUserId(), status, month, pageable);
        return ApiResponse.success(response);
    }
}