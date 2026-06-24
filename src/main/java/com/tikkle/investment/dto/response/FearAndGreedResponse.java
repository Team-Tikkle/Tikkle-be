package com.tikkle.investment.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FearAndGreedResponse {
    private String name;
    private List<FearAndGreedData> data;

    @Getter
    @Setter
    public static class FearAndGreedData {
        private String value;
        private String value_classification;
        private String timestamp;
        private String time_until_update;
    }
}