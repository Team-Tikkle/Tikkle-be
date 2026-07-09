package com.tikkle.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record CoinGeckoGlobalResponse(GlobalData data) {
    public GlobalData getData() {
        return data;
    }

    public record GlobalData(
            @JsonProperty("active_cryptocurrencies")
            int activeCryptocurrencies,
            
            @JsonProperty("market_cap_percentage")
            Map<String, Double> marketCapPercentage
    ) {
        public Map<String, Double> getMarket_cap_percentage() {
            return marketCapPercentage;
        }
    }
}