package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "결제 대시보드 조회 응답 DTO")
public record PaymentDashboardResponse(
        @Schema(description = "이번 달 총 결제 금액", example = "150000")
        long totalPayment,

        @Schema(description = "이번 달 총 투자된 잔돈", example = "12000")
        long totalInvestedChange,

        @Schema(description = "이번 달 투자되지 않은 잔돈", example = "3000")
        long totalUninvested,

        @Schema(description = "이번 달 매수 승인 대기 건수 (월 무관 전체 대기 건수)", example = "2")
        long pendingCount,

        @Schema(description = "가장 최근 결제 감지 시각 (월 무관, 결제 이력 없으면 null). 리스너 유실을 사용자가 스스로 인지하게 하는 용도", example = "2026-07-20T14:32:11")
        LocalDateTime lastPaymentDetectedAt,

        @Schema(description = "이번 달 카테고리별 결제 금액 목록")
        List<CategorySpending> categorySpending
) {
    @Schema(description = "카테고리별 결제 내역")
    public record CategorySpending(
            @Schema(description = "가맹점 카테고리", example = "CAFE")
            String category,

            @Schema(description = "원본 결제 금액 합산", example = "45000")
            long amount
    ) {}
}