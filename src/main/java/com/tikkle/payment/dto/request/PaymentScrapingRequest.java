package com.tikkle.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "결제 푸시 알림 스크래핑 전송 요청")
public record PaymentScrapingRequest(
        @Schema(description = "사용자 ID", example = "1")
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 양수여야 합니다.")
        Long userId,

        @Schema(description = "결제된 카드사명 (50자 이하)", example = "KBANK")
        @NotBlank(message = "카드사명은 필수입니다.")
        @Size(max = 50, message = "카드사명은 50자 이하여야 합니다.")
        String cardCompany,

        @Schema(description = "카드 번호 마지막 4자리 (숫자 4자리)", example = "1234")
        @NotBlank(message = "카드 번호 마지막 4자리는 필수입니다.")
        @Pattern(regexp = "[0-9]{4}", message = "카드 번호 마지막 4자리는 숫자 4자리여야 합니다.")
        String cardNumberLast4,

        @Schema(description = "결제된 가맹점 이름 (100자 이하)", example = "스타벅스 강남점")
        @NotBlank(message = "가맹점명은 필수입니다.")
        @Size(max = 100, message = "가맹점명은 100자 이하여야 합니다.")
        String merchant,

        @Schema(description = "실제 결제 금액", example = "4500")
        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        Integer amount,

        @Schema(description = "안드로이드 클라이언트에서 생성한 고유 트랜잭션 ID (멱등성 보장용, 255자 이하)", example = "tx_123456789")
        @NotBlank(message = "트랜잭션 ID는 필수입니다.")
        @Size(max = 255, message = "트랜잭션 ID는 255자 이하여야 합니다.")
        String transactionId
) {}