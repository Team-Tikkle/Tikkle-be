package com.tikkle.settings.dto.request;

import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "카테고리별 잔돈 규칙 변경 요청")
public record UpdateSpareChangeRulesRequest(
        @Schema(description = "변경할 카테고리 잔돈 규칙 리스트", example = "[\n" +
                "  {\"category\": \"CAFE\", \"ruleType\": \"ROUND_UP_10000\"},\n" +
                "  {\"category\": \"MART\", \"ruleType\": \"ROUND_UP_10000\"},\n" +
                "  {\"category\": \"FOOD\", \"ruleType\": \"ROUND_UP_10000\"},\n" +
                "  {\"category\": \"SHOPPING\", \"ruleType\": \"ROUND_UP_10000\"},\n" +
                "  {\"category\": \"TRAFFIC\", \"ruleType\": \"ROUND_UP_10000\"},\n" +
                "  {\"category\": \"CULTURE\", \"ruleType\": \"ROUND_UP_10000\"},\n" +
                "  {\"category\": \"ETC\", \"ruleType\": \"ROUND_UP_10000\"}\n" +
                "]")
        @NotNull(message = "잔돈 규칙 목록은 필수입니다.")
        @Size(min = 7, max = 7, message = "모든 카테고리에 대한 잔돈 규칙을 전송해야 합니다.")
        @Valid
        List<RuleItem> rules
) {
    @AssertTrue(message = "동일한 결제 카테고리는 중복될 수 없습니다.")
    private boolean isCategoriesUnique() {
        if (rules == null) {
            return true;
        }
        long distinct = rules.stream()
                .map(RuleItem::category)
                .distinct()
                .count();
        return distinct == rules.size();
    }

    @Schema(description = "개별 잔돈 규칙 변경 항목")
    public record RuleItem(
            @Schema(description = "결제 카테고리 유형", example = "CAFE", allowableValues = {"CAFE", "MART", "FOOD", "SHOPPING", "TRAFFIC", "CULTURE", "ETC"})
            @NotNull(message = "결제 카테고리는 필수입니다.")
            PaymentCategory category,

            @Schema(description = "잔돈 저축 규칙 유형", example = "ROUND_UP_10000", allowableValues = {"ROUND_UP_10000", "ROUND_UP_20000", "ROUND_UP_30000", "ROUND_UP_40000", "ROUND_UP_50000", "PERCENT_10", "PERCENT_15", "PERCENT_20", "PERCENT_25", "PERCENT_30"})
            @NotNull(message = "잔돈 규칙 유형은 필수입니다.")
            RuleType ruleType
    ) {
    }
}