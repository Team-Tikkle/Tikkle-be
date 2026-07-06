package com.tikkle.payment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.service.OrderApprovalService;
import com.tikkle.payment.swagger.OrderApprovalSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tikkle.auth.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 매수 승인 및 거절 관련 API 엔드포인트를 제공하는 컨트롤러입니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/{eventId}")
public class OrderApprovalController implements OrderApprovalSwagger {
    private final OrderApprovalService orderApprovalService;

    /**
     * 대기 중인 결제 건에 대해 매수를 승인합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param eventId 승인할 결제 이벤트 ID
     * @return 성공 응답
     */
    @Override
    @PostMapping("/approve")
    public ApiResponse<?> approveOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        orderApprovalService.approveOrder(userDetails.getUserId(), eventId);
        return ApiResponse.successWithNoData();
    }

    /**
     * 대기 중인 결제 건에 대해 매수를 거절합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param eventId 거절할 결제 이벤트 ID
     * @return 성공 응답
     */
    @Override
    @PostMapping("/reject")
    public ApiResponse<?> rejectOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        orderApprovalService.rejectOrder(userDetails.getUserId(), eventId);
        return ApiResponse.successWithNoData();
    }
}