package com.tikkle.payment.dto.request;

import com.tikkle.payment.entity.enums.PaymentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "카테고리 수정 요청 DTO")
public record CategoryUpdateRequest(
        @Schema(description = "변경할 카테고리 (예: CAFE, FOOD 등)", example = "CAFE")
        @NotNull(message = "변경할 카테고리는 필수입니다.")
        PaymentCategory category
) {}
