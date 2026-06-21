package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "결제 푸시 알림 스크래핑 결과 응답 DTO")
public record PaymentScrapingResponse(
        @Schema(description = "저장된 결제 원장 ID", example = "10293", nullable = true)
        Long paymentEventId,
        @Schema(description = "결과에 따른 프론트엔드 액션 타입", example = "ORDER_REQUESTED")
        PaymentActionType actionType,
        @Schema(description = "AI에 의해 정제된 가맹점 이름 (키워드)", example = "스타벅스")
        String cleanMerchantName,
        @Schema(description = "원본 결제 금액", example = "4560")
        int paymentAmount,
        @Schema(description = "규칙에 의해 계산된 발생 잔돈", example = "440")
        int spareChange,
        @Schema(description = "매수 대상 코인 마켓 코드 (자동 투자 시)", example = "KRW-BTC", nullable = true)
        String market,
        @Schema(description = "매수 대상 코인명 (자동 투자 시)", example = "비트코인", nullable = true)
        String coinName,
        @Schema(description = "체결 평단가 (자동 투자 시)", example = "92000000", nullable = true)
        BigDecimal executedPrice,
        @Schema(description = "체결 수량 (자동 투자 시)", example = "0.00005434", nullable = true)
        BigDecimal executedVolume
) {}