package com.tikkle.payment.dto.response;

public record PaymentScrapingResponse(
        PaymentActionType actionType,
        String merchant,
        int paymentAmount,
        int spareChange,
        String ticker,
        String stockName
) {}