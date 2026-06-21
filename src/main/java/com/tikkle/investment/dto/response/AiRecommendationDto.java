package com.tikkle.investment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 종목 추천 결과 DTO")
public record AiRecommendationDto(
        @Schema(description = "추천 마켓 코드", example = "KRW-BTC")
        String market,
        @Schema(description = "추천 코인명", example = "비트코인")
        String coinName,
        @Schema(description = "AI 추천 사유", example = "변동성이 낮아 안정적인 수익을 기대할 수 있습니다.")
        String reason
) {}