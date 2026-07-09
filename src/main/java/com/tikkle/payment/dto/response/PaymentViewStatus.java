package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프론트엔드 노출용 결제 상태")
public enum PaymentViewStatus {
    PENDING,
    INVESTED,
    CANCELED
}