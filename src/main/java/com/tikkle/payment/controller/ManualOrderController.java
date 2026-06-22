package com.tikkle.payment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.service.ManualOrderService;
import com.tikkle.payment.swagger.ManualOrderSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tikkle.auth.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/payments/{eventId}")
@RequiredArgsConstructor
public class ManualOrderController implements ManualOrderSwagger {

    private final ManualOrderService manualOrderService;

    @Override
    @PostMapping("/approve")
    public ApiResponse<?> approveOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        manualOrderService.approveOrder(userDetails.getUserId(), eventId);
        return ApiResponse.success("수동 매수가 성공적으로 승인 및 체결되었습니다.", null);
    }

    @Override
    @PostMapping("/reject")
    public ApiResponse<?> rejectOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        manualOrderService.rejectOrder(userDetails.getUserId(), eventId);
        return ApiResponse.success("수동 매수가 성공적으로 거절되었습니다.", null);
    }
}