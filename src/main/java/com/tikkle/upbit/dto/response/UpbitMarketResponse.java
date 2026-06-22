package com.tikkle.upbit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitMarketResponse(
        @JsonProperty("market") String market,
        @JsonProperty("korean_name") String koreanName,
        @JsonProperty("english_name") String englishName
) {}