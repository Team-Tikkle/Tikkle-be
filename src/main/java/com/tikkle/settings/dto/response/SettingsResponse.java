package com.tikkle.settings.dto.response;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;

import java.util.List;

public record SettingsResponse(
        ExecutionMode executionMode,
        List<CategoryRule> spareChangeRules
) {
    public record CategoryRule(PaymentCategory category, RuleType ruleType) {
    }
}
