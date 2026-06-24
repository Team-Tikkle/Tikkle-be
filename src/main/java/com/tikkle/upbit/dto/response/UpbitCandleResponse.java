package com.tikkle.upbit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record UpbitCandleResponse(
        @JsonProperty("market") String market,
        @JsonProperty("opening_price") BigDecimal openingPrice,
        @JsonProperty("trade_price") BigDecimal tradePrice,
        @JsonProperty("candle_acc_trade_price") BigDecimal candleAccTradePrice
) {
    public double getChangeRate() {
        if (openingPrice == null || tradePrice == null || openingPrice.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return tradePrice.subtract(openingPrice)
                .divide(openingPrice, 4, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }
}