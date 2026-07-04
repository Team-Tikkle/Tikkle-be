package com.tikkle.payment.dto.response;

public enum PaymentActionType {
    PENDING_PURCHASE,
    IGNORE_DUPLICATE,
    IGNORE_CARD_MISMATCH,
    IGNORE_NO_SPARE_CHANGE,
    IGNORE_MINIMUM_AMOUNT_UNMET
}