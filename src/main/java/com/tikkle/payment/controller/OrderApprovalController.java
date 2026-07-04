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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/{eventId}")
public class OrderApprovalController implements OrderApprovalSwagger {
    private final OrderApprovalService orderApprovalService;

    @Override
    @PostMapping("/approve")
    public ApiResponse<?> approveOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        orderApprovalService.approveOrder(userDetails.getUserId(), eventId);
        return ApiResponse.successWithNoData();
    }

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