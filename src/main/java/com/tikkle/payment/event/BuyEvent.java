package com.tikkle.payment.event;

public record BuyEvent(
        Long paymentEventId,
        Long userId,
        String ticker,
        String stockName,
        int amount
) {}