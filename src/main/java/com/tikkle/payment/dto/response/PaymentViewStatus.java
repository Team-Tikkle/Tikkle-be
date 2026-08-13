package com.tikkle.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프론트엔드 노출용 결제 상태")
public enum PaymentViewStatus {
    PENDING,        // 사용자의 매수 승인 대기 (승인/거절 가능)
    IN_PROGRESS,    // 승인 완료 후 업비트 입금·체결 진행 중 (승인/거절 불가)
    INVESTED,
    CANCELED
}