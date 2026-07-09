package com.tikkle.investment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FearAndGreedResponse(String name, List<FearAndGreedData> data) {
    public List<FearAndGreedData> getData() {
        return data;
    }

    public record FearAndGreedData(
            String value,
            
            @JsonProperty("value_classification")
            String valueClassification,
            
            String timestamp,
            
            @JsonProperty("time_until_update")
            String timeUntilUpdate
    ) {
        public String getValue() {
            return value;
        }
    }
}