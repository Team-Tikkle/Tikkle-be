package com.tikkle.payment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.service.ManualOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "수동 매수", description = "수동 승인 대기 중인 결제 건 처리 API")
@RestController
@RequestMapping("/api/payments/{eventId}")
@RequiredArgsConstructor
public class ManualOrderController {

    private final ManualOrderService manualOrderService;

    @PostMapping("/approve")
    @Operation(summary = "매수 승인", description = "WAITING_APPROVAL 상태의 건을 찾아 즉시 업비트에 매수 요청합니다.")
    public ApiResponse<?> approveOrder(@PathVariable Long eventId) {
        manualOrderService.approveOrder(eventId);
        return ApiResponse.success("수동 매수가 성공적으로 승인 및 체결되었습니다.", null);
    }

    @PostMapping("/reject")
    @Operation(summary = "매수 거절", description = "WAITING_APPROVAL 상태의 건을 NOT_INVESTED 상태로 변경합니다.")
    public ApiResponse<?> rejectOrder(@PathVariable Long eventId) {
        manualOrderService.rejectOrder(eventId);
        return ApiResponse.success("수동 매수가 성공적으로 거절되었습니다.", null);
    }
}
