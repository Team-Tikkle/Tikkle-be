package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 스크래핑 처리 결과 (행동 유형)")
public enum PaymentActionType {
    @Schema(description = "정상적으로 매수 대기(큐 대기) 상태가 됨")
    PENDING_PURCHASE,

    @Schema(description = "잔돈이 0원이라 무시됨")
    IGNORE_NO_SPARE_CHANGE,

    @Schema(description = "최소 투자 금액(5,000원)을 충족하지 못해 무시됨")
    IGNORE_MINIMUM_AMOUNT_UNMET
}