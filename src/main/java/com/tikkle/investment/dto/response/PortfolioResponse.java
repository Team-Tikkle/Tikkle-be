package com.tikkle.investment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "홈(포트폴리오) 화면 조회 응답 DTO")
public record PortfolioResponse(
        @Schema(description = "총 투자 원금 (유저가 잔돈으로 모은 총 원화 금액)", example = "150000")
        long totalPrincipalAmount,

        @Schema(description = "총 평가 금액 (보유 코인 전체 평가 금액 합산)", example = "152000")
        long totalEvaluationAmount,

        @Schema(description = "웹소켓 구독용 유저 보유 코인 마켓 코드 리스트", example = "[\"KRW-BTC\", \"KRW-ETH\"]")
        List<String> holdingMarketCodes,

        @Schema(description = "보유 코인별 상세 현황 리스트")
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
            long principalAmount,

            @Schema(description = "[스냅샷] 현재 업비트 실시간 시세", example = "86000000")
            BigDecimal currentPrice,

            @Schema(description = "[스냅샷] 현재 평가 금액 (수량 * 현재가)", example = "106165")
            long evaluationAmount
    ) {}
}