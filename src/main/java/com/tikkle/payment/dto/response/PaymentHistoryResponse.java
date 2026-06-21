package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "결제 내역 피드 조회 응답 DTO")
public record PaymentHistoryResponse(
        @Schema(description = "결제 이벤트 ID (수동 승인/거절 시 사용)", example = "10293")
        Long id,

        @Schema(description = "정제된 가맹점 이름", example = "스타벅스")
        String merchant,

        @Schema(description = "원본 결제 금액", example = "4560")
        int amount,

        @Schema(description = "발생 잔돈", example = "440")
        int roundUpAmount,

        @Schema(description = "가맹점 카테고리 Enum Name", example = "CAFE")
        String category,

        @Schema(description = "상태 값 (PENDING, INVESTED, CANCELED 중 하나)", example = "PENDING")
        String status,

        @Schema(description = "수동 승인 대기 만료 시간 (PENDING 상태일 때만 존재)", example = "2026-06-22T10:30:00", nullable = true)
        LocalDateTime expiredAt
) {
}