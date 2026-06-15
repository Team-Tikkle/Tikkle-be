package com.tikkle.settings.dto.request;

import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateSpareChangeRulesRequest(
        @NotEmpty(message = "변경할 잔돈 규칙은 최소 1개 이상이어야 합니다.")
        @Valid
        List<RuleItem> rules
) {
    public record RuleItem(
            @NotNull(message = "결제 카테고리는 필수입니다.")
            PaymentCategory category,

            @NotNull(message = "잔돈 규칙 유형은 필수입니다.")
            RuleType ruleType
    ) {
    }
}
