package com.tikkle.settings.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Pattern;

@Schema(description = "케이뱅크 타겟 카드 연동 정보 요청")
public record UpdateKbankAccountRequest(
        @Schema(description = "결제 타겟 카드 끝 4자리", example = "1234")
        @NotBlank(message = "카드 번호 끝 4자리는 필수입니다.")
        @Pattern(regexp = "^\\d{4}$", message = "카드 번호 끝 4자리는 4자리 숫자여야 합니다.")
        String targetCardLast4
) {}