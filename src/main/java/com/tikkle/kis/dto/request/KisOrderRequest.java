package com.tikkle.kis.dto.request;

/**
 * KIS 국내 주식 금액 기반 소수점 매수 요청 DTO
 */
public record KisOrderRequest(
        String accountNumber,
        String ticker,
        int amount,
        String accessToken,
        String appKey,
        String appSecret
) {}