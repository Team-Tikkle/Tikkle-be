package com.tikkle.upbit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record UpbitAccountResponse(
        @JsonProperty("currency") String currency,
        @JsonProperty("balance") BigDecimal balance,
        @JsonProperty("locked") BigDecimal locked,
        @JsonProperty("avg_buy_price") BigDecimal avgBuyPrice,
        @JsonProperty("avg_buy_price_modified") boolean avgBuyPriceModified,
        @JsonProperty("unit_currency") String unitCurrency
) {}