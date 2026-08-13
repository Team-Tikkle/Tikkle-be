package com.tikkle.payment.dto.response;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "승인 이후 진행 중인 결제 건 조회 응답 DTO")
public record InProgressPaymentResponse(
        @Schema(description = "결제 이벤트 ID (SSE 재구독 시 사용)", example = "35")
        Long eventId,

        @Schema(description = "진행 단계", example = "PENDING_DEPOSIT")
        PaymentStatus status,

        @Schema(description = "가맹점 이름", example = "스타벅스")
        String merchant,

        @Schema(description = "원본 결제 금액", example = "4560")
        int amount,

        @Schema(description = "투자될 잔돈", example = "5440")
        int spareChange,

        @Schema(description = "투자 대상 코인 마켓명", example = "KRW-BTC", nullable = true)
        String targetCoinMarket,

        @Schema(description = "투자 대상 코인 이름", example = "비트코인", nullable = true)
        String targetCoinName,

        @Schema(description = "현재 단계가 만료되는 시각", example = "2026-08-05T19:05:03", nullable = true)
        LocalDateTime expiresAt,

        @Schema(description = "결제 감지 시각", example = "2026-08-05T19:01:33")
        LocalDateTime createdAt
) {
    private static final int DEPOSIT_TIMEOUT_SECONDS = 210;
    private static final int TRADE_TIMEOUT_MINUTES = 10;

    public static InProgressPaymentResponse from(PaymentEvent event) {
        return new InProgressPaymentResponse(
                event.getId(),
                event.getStatus(),
                event.getRawMerchant(),
                event.getAmount(),
                event.getSpareChange(),
                event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : null,
                event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : null,
                resolveExpiresAt(event),
                event.getCreatedAt()
        );
    }

    private static LocalDateTime resolveExpiresAt(PaymentEvent event) {
        if (event.getStatus() == PaymentStatus.PENDING_DEPOSIT && event.getDepositRequestedAt() != null) {
            return event.getDepositRequestedAt().plusSeconds(DEPOSIT_TIMEOUT_SECONDS);
        }
        if (event.getStatus() == PaymentStatus.PENDING_TRADE && event.getTradeRequestedAt() != null) {
            return event.getTradeRequestedAt().plusMinutes(TRADE_TIMEOUT_MINUTES);
        }
        return null;
    }
}
