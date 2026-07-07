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

        @Schema(description = "정제된 가맹점 이름", example = "스타벅스")
        String merchant,

        @Schema(description = "원본 결제 금액", example = "4560")
        int amount,

        @Schema(description = "발생 잔돈", example = "440")
        int roundUpAmount,

        @Schema(description = "가맹점 카테고리 Enum Name", example = "CAFE")
        String category,

        @Schema(description = "상태 값 (PENDING, INVESTED, CANCELED 중 하나)", example = "PENDING")
        String status,

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
        String statusStr;
        LocalDateTime expiredAt = null;
        BigDecimal investedVolume = null;
        BigDecimal investedPrice = null;

        if (event.getStatus() == PaymentStatus.PENDING_PURCHASE) {
            statusStr = "PENDING";
            expiredAt = event.getCreatedAt().plusHours(24);
        } else if (event.getStatus() == PaymentStatus.INVESTED) {
            statusStr = "INVESTED";
            investedVolume = event.getInvestedVolume();
            investedPrice = event.getInvestedPrice();
        } else if (event.getStatus() == PaymentStatus.NOT_INVESTED || event.getStatus() == PaymentStatus.FAILED) {
            statusStr = "CANCELED";
        } else {
            statusStr = "PENDING";
        }

        String targetCoinMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : null;
        String targetCoinName = event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : null;

        return new PaymentHistoryResponse(
                event.getId(),
                event.getMerchant(),
                event.getAmount(),
                event.getSpareChange(),
                event.getCategory() != null ? event.getCategory().name() : null,
                statusStr,
                expiredAt,
                targetCoinMarket,
                targetCoinName,
                investedVolume,
                investedPrice,
                event.getCreatedAt()
        );
    }
}