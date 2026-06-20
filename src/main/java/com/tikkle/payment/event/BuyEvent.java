package com.tikkle.payment.event;

public record BuyEvent(
        Long paymentEventId,
        String ticker,
        String stockName,
        int amount
) {}