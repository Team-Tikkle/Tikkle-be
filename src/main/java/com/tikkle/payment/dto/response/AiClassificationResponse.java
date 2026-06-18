package com.tikkle.payment.dto.response;

import com.tikkle.payment.entity.enums.PaymentCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 가맹점 분류 결과 DTO")
public record AiClassificationResponse(
        @Schema(description = "AI가 추출한 가맹점의 핵심 키워드", example = "스타벅스")
        String keyword,
        @Schema(description = "AI가 분류한 7대 카테고리", example = "CAFE")
        PaymentCategory category
) {}