package com.tikkle.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PaymentScrapingRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 양수여야 합니다.")
        Long userId,

        @NotBlank(message = "가맹점명은 필수입니다.")
        String merchant,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        Integer amount,

        @NotNull(message = "잔액은 필수입니다.")
        @PositiveOrZero(message = "잔액은 0 이상이어야 합니다.")
        Integer balance,

        @NotBlank(message = "트랜잭션 ID는 필수입니다.")
        String transactionId
) {}