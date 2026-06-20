package com.tikkle.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * KIS 매수 주문 응답 DTO
 */
public record KisOrderResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        Output output
) {
    public record Output(
            @JsonProperty("KRX_FWDG_ORD_ORGNO") String orderOrgNo,
            @JsonProperty("ODNO") String orderNo,
            @JsonProperty("ORD_TMD") String orderTime,
            @JsonProperty("RVSE_CNCL_DVSN_CD") String cancelDivisionCode
    ) {
    }

    public boolean isSuccess() {
        return "0".equals(resultCode);
    }
}