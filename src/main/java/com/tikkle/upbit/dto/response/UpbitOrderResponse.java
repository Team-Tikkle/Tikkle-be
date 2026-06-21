package com.tikkle.upbit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UpbitOrderResponse(
    String uuid,
    String side,
    @JsonProperty("ord_type") String ordType,
    String state,
    String market,
    @JsonProperty("trades") List<UpbitTrade> trades
) {
    public record UpbitTrade(
        String market,
        String uuid,
        String price,
        String volume,
        String funds,
        String side
    ) {}
}