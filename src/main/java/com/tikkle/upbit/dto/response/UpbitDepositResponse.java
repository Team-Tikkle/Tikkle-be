package com.tikkle.upbit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitDepositResponse(
    String type,
    String uuid,
    String currency,
    String txid,
    String state,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("done_at") String doneAt,
    String amount,
    String fee,
    @JsonProperty("transaction_type") String transactionType
) {}