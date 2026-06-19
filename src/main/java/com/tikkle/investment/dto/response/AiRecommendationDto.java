package com.tikkle.investment.dto.response;

public record AiRecommendationDto(
        String ticker,
        String stockName,
        String reason
) {}