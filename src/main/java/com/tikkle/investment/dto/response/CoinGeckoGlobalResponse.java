package com.tikkle.investment.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CoinGeckoGlobalResponse {
    private GlobalData data;

    @Getter
    @Setter
    public static class GlobalData {
        private int active_cryptocurrencies;
        private Map<String, Double> market_cap_percentage;
    }
}