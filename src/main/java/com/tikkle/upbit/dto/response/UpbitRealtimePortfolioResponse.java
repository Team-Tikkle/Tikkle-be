package com.tikkle.upbit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "실시간 업비트 포트폴리오(홈) 조회 응답 DTO")
public record UpbitRealtimePortfolioResponse(
        @Schema(description = "총 자산 (유저의 원화잔액 + 보유코인 총 금액)", example = "150000")
        long totalPrincipalAmount,

        @Schema(description = "웹소켓 구독용 유저 보유 코인 마켓 코드 리스트", example = "[\"KRW-BTC\", \"KRW-ETH\"]")
        List<String> holdingMarketCodes,

        @Schema(description = "보유 코인별 상세 현황 리스트 (웹소켓 실시간 업데이트 용)")
        List<CoinHoldingDto> holdings
) {
    @Schema(description = "코인 보유 상세 정보")
    public record CoinHoldingDto(
            @Schema(description = "코인 마켓 코드", example = "KRW-BTC")
            String market,

            @Schema(description = "코인 한글명", example = "비트코인")
            String coinName,

            @Schema(description = "보유 중인 소수점 수량", example = "0.00123456")
            BigDecimal quantity,

            @Schema(description = "매수 평단가", example = "85000000")
            BigDecimal averagePurchasePrice,

            @Schema(description = "해당 코인 투자 원금 (수량 * 평단가)", example = "104930")
            long principalAmount
    ) {}
}