package com.tikkle.upbit.dto.response;

public record UpbitDepositResponse(
    String type,
    String uuid,
    String currency,
    String txid,
    String state,
    String created_at,
    String done_at,
    String amount,
    String fee,
    String transaction_type
) {}