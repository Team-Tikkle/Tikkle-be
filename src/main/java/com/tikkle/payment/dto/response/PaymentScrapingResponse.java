package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 푸시 알림 스크래핑 결과 응답 DTO")
public record PaymentScrapingResponse(
        @Schema(description = "결과에 따른 프론트엔드 액션 타입", example = "ORDER_REQUESTED")
        PaymentActionType actionType,
        @Schema(description = "AI에 의해 정제된 가맹점 이름 (키워드)", example = "스타벅스")
        String merchant,
        @Schema(description = "원본 결제 금액", example = "4560")
        int paymentAmount,
        @Schema(description = "규칙에 의해 계산된 발생 잔돈", example = "440")
        int spareChange,
        @Schema(description = "매수 대상 종목 티커 (자동 투자 시)", example = "005930", nullable = true)
        String ticker,
        @Schema(description = "매수 대상 종목명 (자동 투자 시)", example = "삼성전자", nullable = true)
        String stockName
) {}