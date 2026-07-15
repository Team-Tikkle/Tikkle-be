package com.tikkle.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CoinGeckoCategoryDto {
    private String id;
    private String name;

    @JsonProperty("market_cap_change_24h")
    private Double marketCapChange24h;

}