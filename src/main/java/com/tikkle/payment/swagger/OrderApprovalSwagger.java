package com.tikkle.payment.swagger;

import com.tikkle.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.tikkle.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Order Approval", description = "매수 승인/거절 API")
public interface OrderApprovalSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "매수 승인", description = "대기 중인 결제 건에 대해 매수를 승인하고 즉시 체결합니다.")
    ApiResponse<?> approveOrder(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "승인할 결제 이벤트 ID", example = "1", required = true) Long eventId
    );

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "매수 거절", description = "대기 중인 결제 건에 대해 매수를 거절하고 투자를 스킵합니다.")
    ApiResponse<?> rejectOrder(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거절할 결제 이벤트 ID", example = "1", required = true) Long eventId
    );
}