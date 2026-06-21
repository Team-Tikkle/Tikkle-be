package com.tikkle.settings.dto.response;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "설정 조회 응답 DTO")
public record SettingsResponse(
        @Schema(description = "매매 실행 방식 (AUTO / MANUAL)", example = "AUTO")
        ExecutionMode executionMode,
        @Schema(description = "등록된 카테고리 잔돈 규칙 목록")
        List<CategoryRule> spareChangeRules
) {
    @Schema(description = "카테고리 잔돈 규칙 아이템")
    public record CategoryRule(
            @Schema(description = "결제 카테고리 유형", example = "CAFE")
            PaymentCategory category, 
            @Schema(description = "잔돈 저축 규칙 유형", example = "ROUND_UP_10000")
            RuleType ruleType
    ) {
    }
}