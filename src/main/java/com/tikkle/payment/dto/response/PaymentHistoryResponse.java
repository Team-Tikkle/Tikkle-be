package com.tikkle.payment.dto.response;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Schema(description = "결제 내역 피드 조회 응답 DTO")
public record PaymentHistoryResponse(
        @Schema(description = "결제 이벤트 ID (매수 승인/거절 시 사용)", example = "10293")
        Long id,

        @Schema(description = "정제되지 않은 원본 가맹점 이름", example = "스타벅스")
        String merchant,

        @Schema(description = "원본 결제 금액", example = "4560")
        int amount,

        @Schema(description = "발생 잔돈", example = "440")
        int roundUpAmount,

        @Schema(description = "가맹점 카테고리 Enum Name", example = "CAFE")
        String category,

        @Schema(description = "상태 값", example = "PENDING")
        PaymentViewStatus status,

        @Schema(description = "매수 승인 대기 만료 시간 (PENDING 상태일 때만 존재)", example = "2026-06-22T10:30:00", nullable = true)
        LocalDateTime expiredAt,

        @Schema(description = "투자 대상 코인 마켓명", example = "KRW-BTC", nullable = true)
        String targetCoinMarket,

    @Schema(description = "투자 대상 코인 이름", example = "비트코인", nullable = true)
    String targetCoinName,

    @Schema(description = "실제 체결된 코인 수량", example = "0.00001", nullable = true)
    BigDecimal investedVolume,

    @Schema(description = "체결 가중평균 단가", example = "100000000", nullable = true)
    BigDecimal investedPrice,

    @Schema(description = "결제 내역 생성 시간", example = "2026-06-22T10:30:00")
    LocalDateTime createdAt
) {
    public static PaymentHistoryResponse from(PaymentEvent event) {
        PaymentViewStatus viewStatus;
        LocalDateTime expiredAt = null;
        BigDecimal investedVolume = null;
        BigDecimal investedPrice = null;

        if (event.getStatus() == PaymentStatus.PENDING_PURCHASE) {
            viewStatus = PaymentViewStatus.PENDING;
            expiredAt = event.getCreatedAt().plusHours(24);
        } else if (event.getStatus() == PaymentStatus.PENDING_DEPOSIT) {
            // 승인이 끝난 건이므로 재승인 대상이 아니다. 유효시간은 3.5분(SSE 타임아웃과 동일)
            viewStatus = PaymentViewStatus.IN_PROGRESS;
            expiredAt = event.getDepositRequestedAt() != null
                    ? event.getDepositRequestedAt().plusSeconds(210) : null;
        } else if (event.getStatus() == PaymentStatus.PENDING_TRADE) {
            // 매수 주문 접수 후 체결 대기. 10분 경과 시 주문 취소
            viewStatus = PaymentViewStatus.IN_PROGRESS;
            expiredAt = event.getTradeRequestedAt() != null
                    ? event.getTradeRequestedAt().plusMinutes(10) : null;
        } else if (event.getStatus() == PaymentStatus.INVESTED) {
            viewStatus = PaymentViewStatus.INVESTED;
            investedVolume = event.getInvestedVolume();
            investedPrice = event.getInvestedPrice();
        } else if (event.getStatus() == PaymentStatus.NOT_INVESTED || event.getStatus() == PaymentStatus.FAILED) {
            viewStatus = PaymentViewStatus.CANCELED;
        } else {
            // 알 수 없는 상태를 PENDING으로 내보내면 앱이 승인 버튼을 노출하므로 진행 중으로 처리한다
            viewStatus = PaymentViewStatus.IN_PROGRESS;
        }

        String targetCoinMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : null;
        String targetCoinName = event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : null;

        return new PaymentHistoryResponse(
                event.getId(),
                event.getRawMerchant(),
                event.getAmount(),
                event.getSpareChange(),
                event.getCategory() != null ? event.getCategory().name() : null,
                viewStatus,
                expiredAt,
                targetCoinMarket,
                targetCoinName,
                investedVolume,
                investedPrice,
                event.getCreatedAt()
        );
    }
}